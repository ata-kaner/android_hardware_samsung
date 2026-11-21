/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.biometrics.BiometricFingerprintConstants;
import android.hardware.biometrics.BiometricStateListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

/**
 * Optical UDFPS overlay coordinator: dim layer + atomic HBM ({@link HbmController} / {@link MaskView}),
 * illumination circle ({@link LightSourceView}), anti-flash mask ({@link BlackMaskView}), and
 * post-illumination touch-down delivery matching Samsung OneUI's {@code SysUiUdfpsOpticalManager}
 * and {@code OpticalController}.
 *
 * <p>All entry points must be called on the main looper.
 */
public class SamsungOpticalUdfpsService
        implements DisplayStateListener.OnDisplayStateChangedListener,
                   FodController.FodListener,
                   HbmController.Callback {

    private static final String TAG = "SB_OpticalUdfpsService";

    /** WakeLock duration covering Samsung's 10s screen-off HBM window + margin. */
    private static final long WAKELOCK_TIMEOUT_MS = 11_000L;
    private static final long WAKELOCK_RELEASE_DEBOUNCE_MS = 1_500L;
    private static final long MAX_CIRCLE_TIMEOUT_MS = 1_500L;

    /** Delay (~2 frames at 60Hz) after illuminating before delivering touch-down to HAL. */
    private static final long TOUCH_DOWN_DELIVERY_DELAY_MS = 34L;

    /** Delay (~4 frames at 60Hz) for tablet panels with HW self-mask light source (matches OneUI). */
    private static final long TOUCH_DOWN_DELIVERY_DELAY_TABLET_HW_LS_MS = 66L;

    /** Failsafe forced touch-up after capture completes (disabled during enrollment). */
    private static final long CAPTURE_COMPLETE_TOUCH_UP_TIMEOUT_MS = 1_000L;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final DisplayStateListener mDisplayStateListener;
    private final MaskView mMaskView;
    private final BlackMaskView mBlackMaskView;
    private final HbmController mHbmController;
    private final FodController mFod;
    private final IBinder mForegroundToken = new Binder();
    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;
    private final boolean mIsTablet;

    private float mUdfpsBrightness;
    private volatile boolean mUseHwLightSource;

    private int mDisplayState = Display.STATE_UNKNOWN;
    private int mBiometricState = BiometricStateListener.STATE_IDLE;
    private volatile boolean mFingerDown;
    private volatile boolean mTouchDownDelivered;
    private volatile boolean mSessionStartedFromScreenOff;
    private boolean mForegroundSet;
    private boolean mDestroyed;

    private final Runnable mReleaseWakeLockRunnable = this::releaseWakeLockNow;
    private final Runnable mCircleTimeoutRunnable = this::stopCircleIllumination;
    private final Runnable mForcedTouchUpRunnable = this::onForcedTouchUpTimeout;
    private final Runnable mDeliverTouchDownRunnable = this::deliverTouchDown;

    public SamsungOpticalUdfpsService(Context context, FodController fod,
                                      FingerprintSensorInfo sensorInfo) {
        Log.i(TAG, "SamsungOpticalUdfpsService is starting");

        mFod = fod;
        mIsTablet = context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
        mPowerManager = context.getSystemService(PowerManager.class);
        if (mPowerManager != null) {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    TAG + ":OpticalAuth");
            mWakeLock.setReferenceCounted(false);
        } else {
            mWakeLock = null;
        }

        mMaskView = new MaskView(context);
        mBlackMaskView = new BlackMaskView(context);

        mUseHwLightSource = mFod.getClient().isHwLightSourceSupported();
        mMaskView.setUseHwLightSource(mUseHwLightSource);

        mUdfpsBrightness = mMaskView.getDisplaySolution().getUdfpsBrightness();
        Log.i(TAG, "UDFPS target brightness: " + mUdfpsBrightness
                + " hwLightSource=" + mUseHwLightSource + " tablet=" + mIsTablet);

        mMaskView.setUdfpsBrightness(mUdfpsBrightness);
        mMaskView.setSensorInfo(sensorInfo);

        mFod.getClient().setMaskBrightness(Math.round(mUdfpsBrightness));

        mDisplayStateListener = new DisplayStateListener(context, this);
        mMaskView.setDisplayStateListener(mDisplayStateListener);
        mBlackMaskView.setDisplayStateListener(mDisplayStateListener);
        mDisplayStateListener.register();

        mHbmController = new HbmController(mMain, mMaskView, mDisplayStateListener,
                mFod.getClient(), this);

        mFod.setFodListener(this);
    }

    private void onForcedTouchUpTimeout() {
        if (mFingerDown) {
            Log.d(TAG, "Capture-completed touch-up timeout expired; forcing finger-up cleanup");
            onFingerUp();
        }
    }

    private void deliverTouchDown() {
        if (mDestroyed || !mFingerDown) return;
        mTouchDownDelivered = true;
        int alpha = mMaskView.getCurrentAlpha();
        boolean screenOn = mDisplayStateListener.isScreenOn();
        Log.d(TAG, "Delivering touch-down to HAL after " + TOUCH_DOWN_DELIVERY_DELAY_MS
                + "ms (alpha=" + alpha + ", screenOn=" + screenOn + ")");
        mFod.getClient().notifyTouchEvent(true, alpha, screenOn);
    }

    // ---- FodController.FodListener (main looper) ----------------------------

    @Override
    public void onHalConnected() {
        if (mDestroyed) return;
        mUseHwLightSource = mFod.getClient().isHwLightSourceSupported();
        mMaskView.setUseHwLightSource(mUseHwLightSource);
        mFod.getClient().setMaskBrightness(Math.round(mUdfpsBrightness));
    }

    @Override
    public void onHbmChanged(boolean enabled) {
        if (mDestroyed) return;
        mHbmController.onHbmChanged(enabled);
    }

    @Override
    public void onFingerDown(int x, int y) {
        if (mDestroyed) return;
        if (mBiometricState == BiometricStateListener.STATE_IDLE) {
            Log.d(TAG, "onFingerDown ignored while biometric state is IDLE");
            return;
        }
        Log.i(TAG, "onFingerDown: (" + x + ", " + y + ") [screenOn="
                + mDisplayStateListener.isScreenOn() + ", doze=" + mDisplayStateListener.isDoze()
                + ", interactive=" + mDisplayStateListener.isInteractive()
                + ", hbmState=" + mHbmController.getState() + "]");

        mMain.removeCallbacks(mForcedTouchUpRunnable);
        mFingerDown = true;

        acquireWakeLock(WAKELOCK_TIMEOUT_MS);
        setProcessForeground(true);

        if (!mDisplayStateListener.isScreenOn() || mDisplayStateListener.isDoze()
                || !mDisplayStateListener.isInteractive()) {
            mSessionStartedFromScreenOff = true;
            Log.i(TAG, "Touch in AoD/Off -> waking display");
            if (mPowerManager != null) {
                mPowerManager.wakeUp(
                        SystemClock.uptimeMillis(),
                        PowerManager.WAKE_REASON_BIOMETRIC,
                        "SamsungBiometrics:AoD");
            }
        }

        mHbmController.onFingerDown();
        mHbmController.runWhenHbmReady(this::illuminateAndScheduleTouchDown);
    }

    @Override
    public void onFingerUp() {
        if (mDestroyed) return;
        Log.d(TAG, "onFingerUp");
        mFingerDown = false;
        mMain.removeCallbacks(mForcedTouchUpRunnable);
        mMain.removeCallbacks(mDeliverTouchDownRunnable);
        mHbmController.clearPendingTouchAction();

        stopCircleIllumination();
        deliverTouchUpIfNeeded();

        if (mBiometricState == BiometricStateListener.STATE_IDLE) {
            mHbmController.stopSession();
            setProcessForeground(false);
        }
        scheduleReleaseWakeLock(WAKELOCK_RELEASE_DEBOUNCE_MS);
    }

    @Override
    public void onWokeFromAod() {
        if (mDestroyed) return;
        Log.i(TAG, "Woke from AoD, bio=" + mBiometricState);
        if (mBiometricState != BiometricStateListener.STATE_IDLE) {
            mHbmController.onDisplayStateChanged(Display.STATE_ON);
        }
    }

    @Override
    public void onAodStateChanged(boolean armed, boolean fast) {
        if (mDestroyed) return;
        Log.d(TAG, "onAodStateChanged: armed=" + armed + ", fast=" + fast);
    }

    @Override
    public void onOpticalCaptureStarted() {
        if (mDestroyed) return;
        Log.d(TAG, "onOpticalCaptureStarted");
        if (!mFingerDown && mBiometricState != BiometricStateListener.STATE_IDLE) {
            mFingerDown = true;
            acquireWakeLock(WAKELOCK_TIMEOUT_MS);
            setProcessForeground(true);
            mHbmController.onFingerDown();
            mHbmController.runWhenHbmReady(this::startCircleIllumination);
        }
    }

    @Override
    public void onOpticalCaptureFinished() {
        if (mDestroyed) return;
        Log.i(TAG, "onOpticalCaptureFinished: stopping illumination");
        mMain.removeCallbacks(mDeliverTouchDownRunnable);
        mHbmController.clearPendingTouchAction();
        stopCircleIllumination();

        if (mFingerDown && mBiometricState != BiometricStateListener.STATE_ENROLLING) {
            mMain.removeCallbacks(mForcedTouchUpRunnable);
            mMain.postDelayed(mForcedTouchUpRunnable, CAPTURE_COMPLETE_TOUCH_UP_TIMEOUT_MS);
        }
        if (mBiometricState == BiometricStateListener.STATE_IDLE) {
            mHbmController.stopSession();
            setProcessForeground(false);
        }
    }

    // ---- HbmController.Callback (main looper) -------------------------------

    @Override
    public void onScreenOffHbmTimeout() {
        if (mDestroyed) return;
        mFingerDown = false;
        mMain.removeCallbacks(mDeliverTouchDownRunnable);
        mMain.removeCallbacks(mForcedTouchUpRunnable);
        stopCircleIllumination();
        deliverTouchUpIfNeeded();
        releaseWakeLockNow();
    }

    // ---- Authentication Events (BioStateListener) ---------------------------

    public void onAuthenticationAcquired(int acquiredInfo) {
        if (BiometricFingerprintConstants.shouldDisableUdfpsDisplayMode(acquiredInfo)) {
            Log.d(TAG, "Fingerprint image acquired (" + acquiredInfo + "); stopping illumination");
            onOpticalCaptureFinished();
        }
    }

    public void onAuthenticationFailed() {
        Log.i(TAG, "Fingerprint scan failed; stopping illumination");
        mMain.removeCallbacks(mDeliverTouchDownRunnable);
        mHbmController.clearPendingTouchAction();
        stopCircleIllumination();
        mBlackMaskView.hide();
        if (mBiometricState == BiometricStateListener.STATE_IDLE) {
            mHbmController.stopSession();
            setProcessForeground(false);
        }
    }

    public void onAuthenticationError(int errorCode, String errString) {
        Log.i(TAG, "Fingerprint error (" + errorCode + ": " + errString + "); stopping session");
        mFingerDown = false;
        mMain.removeCallbacks(mDeliverTouchDownRunnable);
        mMain.removeCallbacks(mForcedTouchUpRunnable);
        stopCircleIllumination();
        deliverTouchUpIfNeeded();
        mHbmController.stopSession();
        mBlackMaskView.hide();
        setProcessForeground(false);
        releaseWakeLockNow();
    }

    public void onAuthenticationSucceeded() {
        Log.i(TAG, "Fingerprint scan succeeded; stopping illumination and HBM");
        mFingerDown = false;
        mMain.removeCallbacks(mDeliverTouchDownRunnable);
        mMain.removeCallbacks(mForcedTouchUpRunnable);
        stopCircleIllumination();
        deliverTouchUpIfNeeded();

        boolean fromScreenOff = mSessionStartedFromScreenOff || !mDisplayStateListener.isScreenOn();
        mHbmController.stopSession();
        setProcessForeground(false);

        if (fromScreenOff) {
            Log.i(TAG, "Authentication succeeded from Screen-Off/AOD; showing black mask");
            mBlackMaskView.show();
        }
    }

    // ---- DisplayStateListener (main looper) --------------------------------

    @Override
    public void onDisplayStateChanged(int displayState) {
        mMain.post(() -> {
            if (mDestroyed) return;
            mDisplayState = displayState;
            Log.d(TAG, "onDisplayStateChanged: " + DisplayStateListener.stateToString(displayState)
                    + " (bio=" + mBiometricState + ", hbmState=" + mHbmController.getState()
                    + ", fingerDown=" + mFingerDown + ")");

            if (!mFingerDown
                    && (displayState == Display.STATE_DOZE
                            || displayState == Display.STATE_DOZE_SUSPEND
                            || displayState == Display.STATE_OFF)) {
                mHbmController.onDisplayStateChanged(displayState);
            } else if (displayState == Display.STATE_ON) {
                mHbmController.onDisplayStateChanged(displayState);
            }
        });
    }

    // ---- Biometric state (called by SamsungBiometricService on main) --------

    public void onBiometricStateChanged(int newState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(() -> onBiometricStateChanged(newState));
            return;
        }
        if (mDestroyed) return;
        Log.i(TAG, "onBiometricStateChanged: " + mBiometricState + " -> " + newState);
        mBiometricState = newState;

        if (newState != BiometricStateListener.STATE_IDLE) {
            boolean screenOff = !mDisplayStateListener.isScreenOn()
                    || mDisplayStateListener.isDoze()
                    || !mDisplayStateListener.isInteractive();
            mSessionStartedFromScreenOff = screenOff;
            setProcessForeground(true);
            mHbmController.startSession();
        } else {
            mFingerDown = false;
            mSessionStartedFromScreenOff = false;
            mMain.removeCallbacks(mDeliverTouchDownRunnable);
            mMain.removeCallbacks(mForcedTouchUpRunnable);
            stopCircleIllumination();
            deliverTouchUpIfNeeded();
            mHbmController.stopSession();
            setProcessForeground(false);
            releaseWakeLockNow();
        }
    }

    // ---- Illumination & Touch Delivery --------------------------------------

    private void illuminateAndScheduleTouchDown() {
        if (mDestroyed || !mFingerDown) return;
        startCircleIllumination();
        long delayMs = (mIsTablet && mUseHwLightSource)
                ? TOUCH_DOWN_DELIVERY_DELAY_TABLET_HW_LS_MS
                : TOUCH_DOWN_DELIVERY_DELAY_MS;
        mMain.removeCallbacks(mDeliverTouchDownRunnable);
        mMain.postDelayed(mDeliverTouchDownRunnable, delayMs);
    }

    private void startCircleIllumination() {
        mMain.removeCallbacks(mCircleTimeoutRunnable);
        if (mUseHwLightSource) {
            mFod.getClient().setHwLightSource(true);
        } else {
            mMaskView.showCircle();
        }
        mMain.postDelayed(mCircleTimeoutRunnable, MAX_CIRCLE_TIMEOUT_MS);
    }

    private void stopCircleIllumination() {
        mMain.removeCallbacks(mCircleTimeoutRunnable);
        if (mUseHwLightSource) {
            mFod.getClient().setHwLightSource(false);
        } else {
            mMaskView.hideCircle();
        }
    }

    private void deliverTouchUpIfNeeded() {
        if (mTouchDownDelivered) {
            mTouchDownDelivered = false;
            mFod.getClient().notifyTouchEvent(false, 0, mDisplayStateListener.isScreenOn());
        }
    }

    // ---- WakeLock Management ------------------------------------------------

    private void acquireWakeLock(long timeoutMs) {
        if (mWakeLock == null) return;
        try {
            mMain.removeCallbacks(mReleaseWakeLockRunnable);
            mWakeLock.acquire(timeoutMs);
        } catch (Exception e) {
            Log.w(TAG, "acquireWakeLock failed", e);
        }
    }

    private void scheduleReleaseWakeLock(long delayMs) {
        if (mWakeLock == null) return;
        mMain.removeCallbacks(mReleaseWakeLockRunnable);
        mMain.postDelayed(mReleaseWakeLockRunnable, delayMs);
    }

    private void releaseWakeLockNow() {
        if (mWakeLock == null) return;
        try {
            mMain.removeCallbacks(mReleaseWakeLockRunnable);
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
                Log.d(TAG, "Auth wake lock released");
            }
        } catch (Exception e) {
            Log.w(TAG, "releaseWakeLockNow failed", e);
        }
    }

    // ---- Lifecycle ----------------------------------------------------------

    public void destroy() {
        Log.i(TAG, "Destroying SamsungOpticalUdfpsService");
        mDestroyed = true;
        mMain.removeCallbacksAndMessages(null);
        stopCircleIllumination();
        deliverTouchUpIfNeeded();
        releaseWakeLockNow();
        mFod.setFodListener(null);
        mDisplayStateListener.unregister();
        mBlackMaskView.hide();
        mHbmController.destroy();
        setProcessForeground(false);
    }

    private void setProcessForeground(boolean foreground) {
        if (mForegroundSet == foreground) return;
        mForegroundSet = foreground;
        try {
            ActivityManager.getService().setProcessImportant(
                    mForegroundToken, Process.myPid(), foreground, TAG);
        } catch (Exception e) {
            Log.e(TAG, "setProcessForeground failed", e);
        }
    }
}
