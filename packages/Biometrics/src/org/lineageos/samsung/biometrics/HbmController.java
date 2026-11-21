/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.os.Handler;
import android.util.Log;
import android.view.Display;

/**
 * 4-state optical HBM controller ported from Samsung OneUI's {@code HbmController}:
 * {@code INIT}, {@code DISPLAY_ON}, {@code DISPLAY_OFF}, and {@code DISPLAY_LIMIT}.
 *
 * <p>Coordinates {@link MaskView} visibility with real hardware HBM readiness reported by
 * {@code ISehFingerprintCallback.onHbmChanged} ({@code /sys/class/lcd/panel/actual_mask_brightness}),
 * queues illumination/touch-down actions until HBM has settled, and enforces Samsung's screen-off
 * auto-off (10s) and stuck-HBM poll (300ms) safety timers.
 */
public final class HbmController {
    private static final String TAG = "SB_HbmController";

    /** Screen-off HBM auto-off failsafe (matches Samsung OpticalController msg 1). */
    public static final long SCREEN_OFF_HBM_TIMEOUT_MS = 10_000L;

    /** Stuck-HBM verification poll after hiding MaskView (matches Samsung OpticalController msg 3). */
    private static final long HBM_OFF_VERIFY_DELAY_MS = 300L;

    /** Failsafe ceiling when waiting for actual_mask_brightness sysfs_notify on touch-down. */
    private static final long HBM_READY_FALLBACK_MS = 150L;

    public enum State {
        INIT,
        DISPLAY_ON,
        DISPLAY_OFF,
        DISPLAY_LIMIT
    }

    public interface Callback {
        void onScreenOffHbmTimeout();
    }

    private final Handler mMain;
    private final MaskView mMaskView;
    private final DisplayStateListener mDisplayStateListener;
    private final SehFingerprintClient mClient;
    private final Callback mCallback;

    private State mState = State.INIT;
    private boolean mSessionActive;
    private boolean mHbmEnabled;
    private Runnable mPendingTouchAction;

    private final Runnable mScreenOffTimeoutRunnable = this::onScreenOffTimeout;
    private final Runnable mHbmOffVerifyRunnable = this::verifyHbmOff;
    private final Runnable mHbmReadyFallbackRunnable = this::onHbmReadyFallback;

    public HbmController(Handler main, MaskView maskView,
                         DisplayStateListener displayStateListener,
                         SehFingerprintClient client, Callback callback) {
        mMain = main;
        mMaskView = maskView;
        mDisplayStateListener = displayStateListener;
        mClient = client;
        mCallback = callback;
    }

    private void onScreenOffTimeout() {
        if (mState == State.DISPLAY_LIMIT) {
            Log.w(TAG, "Screen-off HBM failsafe timeout expired (" + SCREEN_OFF_HBM_TIMEOUT_MS + "ms)");
            turnOffHbm(false);
            transitionTo(State.DISPLAY_OFF);
            if (mCallback != null) {
                mCallback.onScreenOffHbmTimeout();
            }
        }
    }

    private void verifyHbmOff() {
        if (!mMaskView.isShowing() && mHbmEnabled) {
            boolean hwEnabled = mClient.isHbmEnabled();
            if (!hwEnabled) {
                Log.d(TAG, "HBM-off verify confirmed actual_mask_brightness == 0");
                onHbmChanged(false);
            } else {
                Log.w(TAG, "HBM still reported active 300ms after hiding MaskView; forcing cleanup");
                onHbmChanged(false);
            }
        }
    }

    private void onHbmReadyFallback() {
        if (mPendingTouchAction != null) {
            boolean hwEnabled = mClient.isHbmEnabled();
            Log.w(TAG, "HBM ready fallback fired (hwEnabled=" + hwEnabled + "); dispatching action");
            if (hwEnabled) {
                mHbmEnabled = true;
            }
            flushPendingTouchAction();
        }
    }

    public State getState() {
        return mState;
    }

    public boolean isHbmEnabled() {
        if (mHbmEnabled) return true;
        boolean hw = mClient.isHbmEnabled();
        if (hw) {
            mHbmEnabled = true;
        }
        return hw;
    }

    public void startSession() {
        mSessionActive = true;
        if (mDisplayStateListener.isScreenOn() && mDisplayStateListener.isInteractive()) {
            transitionTo(State.DISPLAY_ON);
            turnOnHbm();
        } else {
            transitionTo(State.DISPLAY_OFF);
        }
    }

    public void stopSession() {
        mSessionActive = false;
        clearPendingTouchAction();
        mMain.removeCallbacks(mScreenOffTimeoutRunnable);
        turnOffHbm(true);
        transitionTo(State.INIT);
    }

    public void onFingerDown() {
        if (mState == State.INIT) {
            startSession();
        }
        if (mState == State.DISPLAY_OFF) {
            transitionTo(State.DISPLAY_LIMIT);
            mMain.removeCallbacks(mScreenOffTimeoutRunnable);
            mMain.postDelayed(mScreenOffTimeoutRunnable, SCREEN_OFF_HBM_TIMEOUT_MS);
            turnOnHbm();
        } else if (mState == State.DISPLAY_ON || mState == State.DISPLAY_LIMIT) {
            if (!mMaskView.isShowing()) {
                turnOnHbm();
            }
        }
    }

    public void onScreenOffFingerUp() {
        if (mState == State.DISPLAY_LIMIT) {
            clearPendingTouchAction();
            mMain.removeCallbacks(mScreenOffTimeoutRunnable);
            turnOffHbm(true);
            transitionTo(State.DISPLAY_OFF);
        }
    }

    public void onDisplayStateChanged(int displayState) {
        if (!mSessionActive && mState == State.INIT) {
            return;
        }

        if (displayState == Display.STATE_ON && mDisplayStateListener.isInteractive()) {
            mMain.removeCallbacks(mScreenOffTimeoutRunnable);
            if (mState == State.DISPLAY_OFF || mState == State.DISPLAY_LIMIT) {
                transitionTo(State.DISPLAY_ON);
                turnOnHbm();
            }
        } else if (displayState == Display.STATE_OFF
                || displayState == Display.STATE_DOZE
                || displayState == Display.STATE_DOZE_SUSPEND) {
            if (mState == State.DISPLAY_ON || mState == State.DISPLAY_LIMIT) {
                mMain.removeCallbacks(mScreenOffTimeoutRunnable);
                turnOffHbm(true);
                transitionTo(State.DISPLAY_OFF);
            }
        }
    }

    /**
     * Runs {@code action} immediately if hardware HBM is already active; otherwise queues it
     * to execute as soon as {@link #onHbmChanged(boolean)} reports HBM enabled.
     */
    public void runWhenHbmReady(Runnable action) {
        clearPendingTouchAction();
        if (action == null) return;

        if (isHbmEnabled()) {
            action.run();
        } else {
            Log.d(TAG, "HBM not yet active; queuing illumination action");
            mPendingTouchAction = action;
            mMain.postDelayed(mHbmReadyFallbackRunnable, HBM_READY_FALLBACK_MS);
        }
    }

    public void clearPendingTouchAction() {
        mPendingTouchAction = null;
        mMain.removeCallbacks(mHbmReadyFallbackRunnable);
    }

    public void onHbmChanged(boolean enabled) {
        Log.i(TAG, "onHbmChanged: " + mHbmEnabled + " -> " + enabled + " (state=" + mState + ")");
        mHbmEnabled = enabled;
        mMaskView.onHbmStateChanged(enabled);

        if (enabled) {
            flushPendingTouchAction();
        } else {
            mMain.removeCallbacks(mHbmOffVerifyRunnable);
            if (mState == State.DISPLAY_LIMIT && !mMaskView.isShowing()) {
                mMain.removeCallbacks(mScreenOffTimeoutRunnable);
                transitionTo(State.DISPLAY_OFF);
            }
        }
    }

    public void destroy() {
        mMain.removeCallbacks(mScreenOffTimeoutRunnable);
        mMain.removeCallbacks(mHbmOffVerifyRunnable);
        clearPendingTouchAction();
        mSessionActive = false;
        mMaskView.hide(false);
        mMaskView.stop();
        transitionTo(State.INIT);
    }

    private void turnOnHbm() {
        mMain.removeCallbacks(mHbmOffVerifyRunnable);
        mMaskView.show();
        mMaskView.start();
    }

    private void turnOffHbm(boolean deferRefreshRateRelease) {
        mMaskView.hide(deferRefreshRateRelease && mHbmEnabled);
        mMaskView.stop();
        if (mHbmEnabled) {
            mMain.removeCallbacks(mHbmOffVerifyRunnable);
            mMain.postDelayed(mHbmOffVerifyRunnable, HBM_OFF_VERIFY_DELAY_MS);
        }
    }

    private void flushPendingTouchAction() {
        mMain.removeCallbacks(mHbmReadyFallbackRunnable);
        Runnable action = mPendingTouchAction;
        mPendingTouchAction = null;
        if (action != null) {
            action.run();
        }
    }

    private void transitionTo(State next) {
        if (mState == next) return;
        Log.i(TAG, "HBM state: " + mState + " -> " + next);
        mState = next;
    }
}
