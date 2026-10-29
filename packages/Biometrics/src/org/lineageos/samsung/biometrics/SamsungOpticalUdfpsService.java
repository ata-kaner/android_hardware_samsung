/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.biometrics.BiometricFingerprintConstants;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Display;

/**
 * Optical UDFPS overlay logic: dim layer, light-source circle, black mask after HBM.
 *
 * <p>Touch events and FoD TSP state are managed by {@link FodController}, which notifies
 * this service via {@link FodController.FodListener}. Fingerprint auth events are received
 * from {@link BioStateListener}. All entry points must be called on the main thread.
 */
public class SamsungOpticalUdfpsService
        implements DisplayStateListener.OnDisplayStateChangedListener,
                   FodController.FodListener {

    private static final String TAG = "SB_OpticalUdfpsService";

    private static final long WAKELOCK_TIMEOUT_MS = 5000;
    private static final long WAKELOCK_RELEASE_DEBOUNCE_MS = 1500;
    private static final long MAX_CIRCLE_TIMEOUT_MS = 1500;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final DisplayStateListener mDisplayStateListener;
    private final MaskView mMaskView;
    private final BlackMaskView mBlackMaskView;
    private final FodController mFod;
    private final DisplayBrightnessMonitor mBrightnessMonitor;
    private final IBinder mForegroundToken = new Binder();
    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;

    private float mUdfpsBrightness;

    private int mDisplayState = Display.STATE_UNKNOWN;
    private int mBiometricState = 0; // STATE_IDLE
    private volatile boolean mSessionActive;
    private volatile boolean mFingerDown;
    private volatile boolean mSessionStartedFromScreenOff;
    private boolean mDestroyed;

    private final Runnable mReleaseWakeLockRunnable = this::releaseWakeLockNow;
    private final Runnable mCircleTimeoutRunnable = this::stopCircleIllumination;

    public SamsungOpticalUdfpsService(Context context, FodController fod, FingerprintSensorInfo sensorInfo) {
        Log.i(TAG, "SamsungOpticalUdfpsService is starting");

        mFod = fod;
        mPowerManager = context.getSystemService(PowerManager.class);
        if (mPowerManager != null) {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":OpticalAuth");
            mWakeLock.setReferenceCounted(false);
        } else {
            mWakeLock = null;
        }

        mMaskView = new MaskView(context);
        mBlackMaskView = new BlackMaskView(context);
        mBrightnessMonitor = DisplayBrightnessMonitor.getInstance();

        mUdfpsBrightness = mMaskView.getDisplaySolution().getUdfpsBrightness();
        Log.i(TAG, "UDFPS target brightness: " + mUdfpsBrightness);

        mMaskView.setUdfpsBrightness(mUdfpsBrightness);
        mMaskView.setSensorInfo(sensorInfo);

        mFod.getClient().setMaskBrightness(Math.round(mUdfpsBrightness));

        mDisplayStateListener = new DisplayStateListener(context, this);
        mMaskView.setDisplayStateListener(mDisplayStateListener);
        mDisplayStateListener.register();

        mFod.setFodListener(this);
    }

    // ---- FodController.FodListener (main looper) ----------------------------

    @Override
    public void onFingerDown(int x, int y) {
        if (mDestroyed) return;
        Log.i(TAG, "onFingerDown: (" + x + ", " + y + ") [screenOn="
                + mDisplayStateListener.isScreenOn() + ", doze=" + mDisplayStateListener.isDoze()
                + ", interactive=" + mDisplayStateListener.isInteractive() + "]");
        mFingerDown = true;

        acquireWakeLock(WAKELOCK_TIMEOUT_MS);

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

        showDimLayer();
        startCircleIllumination();
    }

    @Override
    public void onFingerUp() {
        if (mDestroyed) return;
        Log.d(TAG, "onFingerUp");
        mFingerDown = false;

        stopCircleIllumination();
        hideDimLayer();
        scheduleReleaseWakeLock(WAKELOCK_RELEASE_DEBOUNCE_MS);
    }

    @Override
    public void onWokeFromAod() {
        if (mDestroyed) return;
        Log.i(TAG, "Woke from AoD, bio=" + mBiometricState);
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
    }

    @Override
    public void onOpticalCaptureFinished() {
        if (mDestroyed) return;
        Log.i(TAG, "onOpticalCaptureFinished: Immediately stopping illumination");
        stopCircleIllumination();
        hideDimLayer();
    }

    // ---- Authentication Events (BioStateListener) ---------------------------

    /**
     * Sensor finished image capture (good or bad). Turn off illumination circle immediately
     * so user is not blinded while matching runs.
     */
    public void onAuthenticationAcquired(int acquiredInfo) {
        if (BiometricFingerprintConstants.shouldDisableUdfpsDisplayMode(acquiredInfo)) {
            Log.d(TAG, "Fingerprint image acquired (" + acquiredInfo + "); stopping illumination");
            stopCircleIllumination();
            hideDimLayer();
        }
    }

    /**
     * Fingerprint did not match. Stop showing the illumination circle immediately even if finger
     * is still touching the display.
     */
    public void onAuthenticationFailed() {
        Log.i(TAG, "Fingerprint scan failed; stopping illumination");
        stopCircleIllumination();
        hideDimLayer();
        mBlackMaskView.hide();
    }

    /**
     * Fingerprint error occurred (lockout, timeout, cancelled). Stop illumination circle.
     */
    public void onAuthenticationError(int errorCode, String errString) {
        Log.i(TAG, "Fingerprint error (" + errorCode + ": " + errString + "); stopping illumination");
        stopCircleIllumination();
        hideDimLayer();
        mBlackMaskView.hide();
    }

    /**
     * Fingerprint scan succeeded. Stop illumination circle.
     */
    public void onAuthenticationSucceeded() {
        Log.i(TAG, "Fingerprint scan succeeded; stopping illumination");
        stopCircleIllumination();
        hideDimLayer();
        if (mSessionStartedFromScreenOff || !mDisplayStateListener.isScreenOn()) {
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
                    + " (bio=" + mBiometricState + ", active=" + mSessionActive
                    + ", fingerDown=" + mFingerDown + ")");

            if (displayState == Display.STATE_DOZE
                    || displayState == Display.STATE_DOZE_SUSPEND
                    || displayState == Display.STATE_OFF
                    || !mDisplayStateListener.isInteractive()) {
                if (!mFingerDown) {
                    hideDimLayer();
                }
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
        int oldState = mBiometricState;
        mBiometricState = newState;

        if (newState != 0) {
            if (!mDisplayStateListener.isScreenOn() || mDisplayStateListener.isDoze()
                    || !mDisplayStateListener.isInteractive()) {
                mSessionStartedFromScreenOff = true;
            } else {
                mSessionStartedFromScreenOff = false;
            }
        } else {
            mFingerDown = false;
            mSessionStartedFromScreenOff = false;
            stopCircleIllumination();

            hideDimLayer();
            releaseWakeLockNow();
        }
    }

    // ---- Illumination Circle Management -------------------------------------

    private void startCircleIllumination() {
        mMain.removeCallbacks(mCircleTimeoutRunnable);
        mMaskView.showCircle();
        mMain.postDelayed(mCircleTimeoutRunnable, MAX_CIRCLE_TIMEOUT_MS);
    }

    private void stopCircleIllumination() {
        mMain.removeCallbacks(mCircleTimeoutRunnable);
        mMaskView.hideCircle();
    }

    // ---- Dim Layer Management -----------------------------------------------

    private void showDimLayer() {
        if (!mSessionActive) {
            Log.d(TAG, "showDimLayer");
            mSessionActive = true;
            mMaskView.show();
            mMaskView.start();
            setProcessForeground(true);
        }
    }

    private void hideDimLayer() {
        if (mSessionActive) {
            Log.d(TAG, "hideDimLayer");
            mSessionActive = false;
            stopCircleIllumination();
            mMaskView.hide();
            mMaskView.stop();
            setProcessForeground(false);
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
        releaseWakeLockNow();
        mFod.setFodListener(null);
        mDisplayStateListener.unregister();
        mBlackMaskView.hide();
        hideDimLayer();
    }

    private void setProcessForeground(boolean foreground) {
        try {
            ActivityManager.getService().setProcessImportant(
                    mForegroundToken, Process.myPid(), foreground, TAG);
        } catch (Exception e) {
            Log.e(TAG, "setProcessForeground failed", e);
        }
    }
}
