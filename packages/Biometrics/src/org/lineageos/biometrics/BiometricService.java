/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.biometrics;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import java.io.File;
import java.nio.charset.StandardCharsets;

import vendor.samsung.hardware.sysinput.ISehSysInputDev;

/**
 * System service that manages Samsung Fingerprint-on-Display (FoD) mode
 * by coordinating screen state, keyguard state, AoD, and fingerprint
 * gestures through the SysInput HAL.
 *
 * <p>FoD state machine:
 * <pre>
 *   DISABLED      → "0"     — FoD off
 *   SCREEN_ON_FAST→ "1,1,0" — Screen on, biometric possible (keyguard locked)
 *   AOD_STRICT    → "1,0,0" — AoD, distinguishing FP from SingleTap
 *   AOD_FAST      → "1,1,0" — AoD, fast capture after FP gesture (5s burst)
 * </pre>
 *
 * <p>Fingerprint gesture detection in AoD is triggered via the broadcast:
 * {@code org.lineageos.biometrics.action.FINGERPRINT_GESTURE}
 */
public class BiometricService extends Service
        implements BioStateListener.OnBiometricStateChangedListener {
    private static final String TAG = "SB_Service";

    // ========================================================================
    // HAL constants (Reference: SemInputConstanst.java)
    // ========================================================================

    /** Device type: primary touchscreen */
    private static final int TYPE_DEFAULT_TSP = 1;

    /** Property ID 18 maps to the "cmd" sysfs node which controls TSP commands */
    private static final int PROPERTY_CMD = 18;

    /** Command prefixes for FoD operations */
    private static final String SET_FOD_ENABLE = "fod_enable";
    private static final String SET_FOD_ICON_VISIBLE = "fod_icon_visible";
    private static final String SET_FOD_LP_MODE = "fod_lp_mode";
    private static final String SET_FOD_RECT = "set_fod_rect";

    /**
     * FOD value format: "mode,pressFast,strictMode"
     *   Fast mode  = "1,1,0" — low latency, ultrasonic FP capture
     *   Strict mode= "1,0,0" — distinguishes FP press from single-tap gesture
     *   Disabled   = "0"
     */
    private static final String FOD_FAST = "1,1,0";
    private static final String FOD_STRICT = "1,0,0";
    private static final String FOD_OFF = "0";

    /** SysFS path for fingerprint sensor position data */
    private static final String SYSFS_FP_POSITION =
            "/sys/class/fingerprint/fingerprint/position";

    // ========================================================================
    // Sensor area (read from sysfs, parsed once at startup)
    // ========================================================================

    private double mSensorAreaWidth = 9;
    private double mSensorAreaHeight = 4;
    private double mSensorMarginBottom = 13.77;
    private double mSensorMarginLeft = 0;
    private double mSensorActiveArea = 14.80;
    private boolean mFodRectSent = false;

    // ========================================================================
    // Timing
    // ========================================================================

    /** How long to keep fast mode after a FP gesture in AoD before reverting to strict */
    private static final long AOD_FAST_TIMEOUT_MS = 5000;

    /** Delay after SCREEN_OFF before evaluating display state (let DOZE settle) */
    private static final long SCREEN_OFF_SETTLE_MS = 500;

    /** Delay before retrying HAL connection */
    private static final long HAL_RETRY_MS = 5000;

    /** Delay before retrying HAL connection after death */
    private static final long HAL_DEATH_RETRY_MS = 2000;

    // ========================================================================
    // Settings & intents
    // ========================================================================

    private static final String SETTING_SCREEN_OFF_UDFPS = "screen_off_udfps_enabled";

    /**
     * Broadcast action for fingerprint gesture detection during AoD.
     * Kept as a fallback/testing mechanism. Primary signaling uses the
     * {@link #PROP_FP_GESTURE} system property set by the fingerprint HAL.
     *
     * Test with: adb shell am broadcast -a org.lineageos.biometrics.action.FINGERPRINT_GESTURE
     */
    public static final String ACTION_FINGERPRINT_GESTURE =
            "org.lineageos.biometrics.action.FINGERPRINT_GESTURE";

    /**
     * System property set by the fingerprint HAL (vendor process) when a
     * finger touches the UDFPS sensor area during AoD. Value is "1" when
     * a gesture is detected, reset to "0" after processing.
     */
    private static final String PROP_FP_GESTURE = "vendor.fingerprint.gesture";

    private static final String NOTIFICATION_CHANNEL_ID = "samsung_biometrics_fod";

    // ========================================================================
    // FoD state machine
    // ========================================================================

    private enum FodState {
        /** FoD disabled — HAL value "0" */
        DISABLED,
        /** Screen on, keyguard locked — HAL value "1,1,0" (fast) */
        SCREEN_ON_FAST,
        /** AoD, awaiting FP gesture — HAL value "1,0,0" (strict) */
        AOD_STRICT,
        /** AoD, FP gesture detected, 5s fast burst — HAL value "1,1,0" (fast) */
        AOD_FAST
    }

    private FodState mCurrentState = FodState.DISABLED;
    private boolean mScreenOffUdfpsEnabled = false;

    /**
     * Current biometric state from {@link BiometricStateListener}.
     *   0 = IDLE, 1 = ENROLLING, 2 = KEYGUARD_AUTH, 3 = BP_AUTH
     */
    private volatile int mBiometricState = 0;
    private static final int BIO_STATE_IDLE = 0;

    // ========================================================================
    // System services & HAL
    // ========================================================================

    private ISehSysInputDev mSysInputHal;
    private KeyguardManager mKeyguardManager;
    private DisplayManager mDisplayManager;
    private IBinder.DeathRecipient mDeathRecipient;
    private BioStateListener mBioStateListener;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // ========================================================================
    // AoD fast mode timeout — reverts to strict after 5 seconds
    // ========================================================================

    private final Runnable mAodFastTimeout = () -> {
        if (mCurrentState == FodState.AOD_FAST) {
            Log.d(TAG, "AoD fast mode timeout expired, reverting to strict");
            transitionToState(FodState.AOD_STRICT);
        }
    };

    // ========================================================================
    // Screen ON/OFF broadcast receiver
    // ========================================================================

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_ON:
                    Log.d(TAG, "Screen ON");
                    evaluateState();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    Log.d(TAG, "Screen OFF");
                    // Delay evaluation to let display settle into DOZE state
                    mHandler.postDelayed(
                            BiometricService.this::evaluateState,
                            SCREEN_OFF_SETTLE_MS);
                    break;
                case Intent.ACTION_USER_PRESENT:
                    Log.d(TAG, "User present (unlocked)");
                    evaluateState();
                    break;
            }
        }
    };

    // ========================================================================
    // Fingerprint gesture detection (property + broadcast fallback)
    // ========================================================================

    /**
     * Property change callback. Fires on ANY system property change;
     * we filter for {@link #PROP_FP_GESTURE} inside the callback.
     * The fingerprint HAL sets vendor.fingerprint.gesture=1 in
     * onPointerDownWithContext when context.isAod is true.
     */
    private final Runnable mPropertyCallback = () -> {
        // Check for fingerprint gesture (AoD fast mode trigger)
        if ("1".equals(SystemProperties.get(PROP_FP_GESTURE, "0"))) {
            SystemProperties.set(PROP_FP_GESTURE, "0");
            mHandler.post(this::onFingerprintGesture);
        }
    };

    /**
     * Broadcast receiver fallback for FP gesture. Useful for testing with:
     * adb shell am broadcast -a org.lineageos.biometrics.action.FINGERPRINT_GESTURE
     */
    private final BroadcastReceiver mFpGestureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_FINGERPRINT_GESTURE.equals(intent.getAction())) {
                onFingerprintGesture();
            }
        }
    };

    // ========================================================================
    // Display state listener (AoD detection)
    // ========================================================================

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        evaluateState();
                    }
                }

                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}
            };

    // ========================================================================
    // Keyguard locked state listener (API 33+)
    // ========================================================================

    private final KeyguardManager.KeyguardLockedStateListener mKeyguardListener =
            isKeyguardLocked -> {
                Log.d(TAG, "Keyguard locked: " + isKeyguardLocked);
                evaluateState();
            };

    // ========================================================================
    // UDFPS setting observer
    // ========================================================================

    private ContentObserver mUdfpsObserver;

    // ========================================================================
    // Service lifecycle
    // ========================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "SamsungBiometricService starting");

        mKeyguardManager = getSystemService(KeyguardManager.class);
        mDisplayManager = getSystemService(DisplayManager.class);

        startForegroundNotification();
        readSensorAreaFromSysFs();
        connectToHal();
        registerAllObservers();

        // Register biometric state listener
        mBioStateListener = new BioStateListener(this, this);
        mBioStateListener.register();

        // Read initial setting value and evaluate state
        mScreenOffUdfpsEnabled = isScreenOffUdfpsEnabled();
        evaluateState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "SamsungBiometricService stopping");
        mHandler.removeCallbacksAndMessages(null);
        if (mBioStateListener != null) {
            mBioStateListener.unregister();
        }
        unregisterAllObservers();
        disconnectFromHal();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ========================================================================
    // State machine core
    // ========================================================================

    /**
     * Evaluate current conditions and transition to the appropriate FoD state.
     * Called whenever any input signal changes (screen, display, keyguard, setting).
     */
    private void evaluateState() {
        FodState desired = computeDesiredState();

        if (desired == mCurrentState) return;

        // Protect AOD_FAST: don't let a re-evaluation override it with AOD_STRICT.
        // The 5-second timeout runnable handles AOD_FAST → AOD_STRICT transitions.
        if (mCurrentState == FodState.AOD_FAST && desired == FodState.AOD_STRICT) {
            return;
        }

        transitionToState(desired);
    }

    /**
     * Compute the desired FoD state from current device conditions.
     */
    private FodState computeDesiredState() {
        int displayState = getDisplayState();

        switch (displayState) {
            case Display.STATE_ON:
            case Display.STATE_VR:
                // Screen on — enable fast mode only when a biometric
                // operation is active (enrolling, keyguard auth, or BP auth)
                if (mBiometricState != BIO_STATE_IDLE) {
                    return FodState.SCREEN_ON_FAST;
                }
                return FodState.DISABLED;

            case Display.STATE_DOZE:
            case Display.STATE_DOZE_SUSPEND:
            case Display.STATE_OFF:
                // Screen off / AoD — strict mode keeps the sensor ready to
                // distinguish a real FP press from a single-tap gesture.
                // Disabled when Screen-Off UDFPS setting is off.
                if (mScreenOffUdfpsEnabled) {
                    return FodState.AOD_STRICT;
                }
                return FodState.DISABLED;

            case Display.STATE_UNKNOWN:
            default:
                return FodState.DISABLED;
        }
    }

    /**
     * Execute a state transition: update internal state, cancel stale timers,
     * and send the corresponding HAL command.
     */
    private void transitionToState(FodState newState) {
        FodState oldState = mCurrentState;
        mCurrentState = newState;

        // Cancel pending AoD fast timeout if leaving AOD_FAST
        if (oldState == FodState.AOD_FAST && newState != FodState.AOD_FAST) {
            mHandler.removeCallbacks(mAodFastTimeout);
        }

        String fodValue;
        switch (newState) {
            case SCREEN_ON_FAST:
            case AOD_FAST:
                fodValue = FOD_FAST;
                break;
            case AOD_STRICT:
                fodValue = FOD_STRICT;
                break;
            case DISABLED:
            default:
                fodValue = FOD_OFF;
                break;
        }

        Log.i(TAG, "FoD: " + oldState + " -> " + newState + " [" + fodValue + "]");
        sendFodCommand(fodValue);
    }

    // ========================================================================
    // Event handlers
    // ========================================================================

    /**
     * Called when a fingerprint gesture is detected during AoD.
     * Switches to fast mode for 5 seconds; resets the timer on repeated gestures.
     */
    private void onFingerprintGesture() {
        if (mCurrentState != FodState.AOD_STRICT && mCurrentState != FodState.AOD_FAST) {
            Log.d(TAG, "FP gesture ignored (not in AoD FoD state: " + mCurrentState + ")");
            return;
        }

        Log.i(TAG, "FP gesture detected in AoD — switching to fast mode");

        // Reset (or start) the 5-second timeout
        mHandler.removeCallbacks(mAodFastTimeout);
        transitionToState(FodState.AOD_FAST);
        mHandler.postDelayed(mAodFastTimeout, AOD_FAST_TIMEOUT_MS);
    }

    /**
     * Called when the Screen-Off UDFPS setting is toggled.
     */
    private void onUdfpsSettingChanged() {
        boolean enabled = isScreenOffUdfpsEnabled();
        Log.i(TAG, "Screen-Off UDFPS setting changed: " + enabled);
        mScreenOffUdfpsEnabled = enabled;

        if (!enabled
                && (mCurrentState == FodState.AOD_STRICT
                    || mCurrentState == FodState.AOD_FAST)) {
            // Setting turned off while in AoD FoD mode — disable immediately
            transitionToState(FodState.DISABLED);
        } else {
            evaluateState();
        }
    }

    /**
     * Called by {@link BioStateListener} when the biometric state changes.
     * States: 0=IDLE, 1=ENROLLING, 2=KEYGUARD_AUTH, 3=BP_AUTH
     */
    @Override
    public void onBiometricStateChanged(int newState) {
        Log.i(TAG, "Biometric state changed: " + newState);
        mBiometricState = newState;
        mHandler.post(this::evaluateState);
    }

    // ========================================================================
    // HAL connection management
    // ========================================================================

    private void connectToHal() {
        try {
            IBinder binder = ServiceManager.getService(
                    "vendor.samsung.hardware.sysinput.ISehSysInputDev/default");
            if (binder == null) {
                Log.e(TAG, "SysInput HAL not found, retrying in "
                        + HAL_RETRY_MS + "ms");
                mHandler.postDelayed(this::connectToHal, HAL_RETRY_MS);
                return;
            }

            mSysInputHal = ISehSysInputDev.Stub.asInterface(binder);

            mDeathRecipient = () -> {
                Log.w(TAG, "SysInput HAL died, reconnecting...");
                mSysInputHal = null;
                mHandler.postDelayed(this::connectToHal, HAL_DEATH_RETRY_MS);
            };
            binder.linkToDeath(mDeathRecipient, 0);

            Log.i(TAG, "Connected to SysInput HAL");

            // Send FOD rect to touchscreen on first connection
            setFodRect();

            // Re-apply current state now that the HAL is (re)connected
            reapplyCurrentState();

        } catch (RemoteException e) {
            Log.e(TAG, "Failed to connect to HAL", e);
            mHandler.postDelayed(this::connectToHal, HAL_RETRY_MS);
        }
    }

    private void disconnectFromHal() {
        if (mSysInputHal != null) {
            try {
                mSysInputHal.asBinder().unlinkToDeath(mDeathRecipient, 0);
            } catch (Exception e) {
                Log.w(TAG, "unlinkToDeath failed", e);
            }
            mSysInputHal = null;
        }
    }

    /**
     * Send a FOD mode command to the touchscreen HAL.
     *
     * @param value one of {@link #FOD_FAST}, {@link #FOD_STRICT}, or {@link #FOD_OFF}
     */
    private void sendFodCommand(String value) {
        if (mSysInputHal == null) {
            Log.w(TAG, "HAL not connected, cannot send FoD=" + value);
            return;
        }
        try {
            int ret = mSysInputHal.setProperty(TYPE_DEFAULT_TSP, PROPERTY_CMD, SET_FOD_ENABLE + "," + value);
            Log.d(TAG, "setProperty(TSP, FOD, " + SET_FOD_ENABLE + "," +value + ") = " + ret);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set FoD property", e);
        }
    }

    /**
     * Re-send the HAL command for the current state (used after HAL reconnection).
     */
    private void reapplyCurrentState() {
        FodState state = mCurrentState;
        // Force re-evaluation in case conditions changed while HAL was down
        mCurrentState = FodState.DISABLED;
        evaluateState();
        // If evaluateState didn't change from DISABLED, explicitly apply it
        if (mCurrentState == FodState.DISABLED && state == FodState.DISABLED) {
            sendFodCommand(FOD_OFF);
        }
    }

    // ========================================================================
    // Observer / listener registration
    // ========================================================================

    private void registerAllObservers() {
        // Screen ON/OFF + user present
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);

        // Fingerprint gesture — primary: property observer from FP HAL
        SystemProperties.addChangeCallback(mPropertyCallback);

        // Fingerprint gesture — fallback: broadcast for adb testing
        IntentFilter fpFilter = new IntentFilter(ACTION_FINGERPRINT_GESTURE);
        registerReceiver(mFpGestureReceiver, fpFilter, Context.RECEIVER_EXPORTED);

        // Display state changes (AoD transitions)
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);

        // Keyguard lock state (API 33+)
        if (mKeyguardManager != null) {
            mKeyguardManager.addKeyguardLockedStateListener(
                    getMainExecutor(), mKeyguardListener);
        }

        // Screen-Off UDFPS setting
        ContentResolver resolver = getContentResolver();
        Uri udfpsUri = Settings.Secure.getUriFor(SETTING_SCREEN_OFF_UDFPS);
        mUdfpsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                onUdfpsSettingChanged();
            }
        };
        resolver.registerContentObserver(udfpsUri, false, mUdfpsObserver,
                UserHandle.USER_ALL);

        Log.d(TAG, "All observers registered");
    }

    private void unregisterAllObservers() {
        try {
            unregisterReceiver(mScreenReceiver);
        } catch (IllegalArgumentException ignored) {}

        try {
            unregisterReceiver(mFpGestureReceiver);
        } catch (IllegalArgumentException ignored) {}

        mDisplayManager.unregisterDisplayListener(mDisplayListener);

        if (mKeyguardManager != null) {
            mKeyguardManager.removeKeyguardLockedStateListener(mKeyguardListener);
        }

        if (mUdfpsObserver != null) {
            getContentResolver().unregisterContentObserver(mUdfpsObserver);
            mUdfpsObserver = null;
        }

        Log.d(TAG, "All observers unregistered");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private int getDisplayState() {
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        return (display != null) ? display.getState() : Display.STATE_UNKNOWN;
    }

    /**
     * Read the Screen-Off UDFPS setting for the current user.
     * Defaults to OFF (0) if not explicitly set.
     */
    private boolean isScreenOffUdfpsEnabled() {
        return Settings.Secure.getIntForUser(
                getContentResolver(),
                SETTING_SCREEN_OFF_UDFPS,
                0 /* default off */,
                UserHandle.USER_CURRENT) != 0;
    }

    // ========================================================================
    // FOD rect — sensor position setup
    // ========================================================================

    /**
     * Read the fingerprint sensor position from sysfs.
     * Format: "marginBottom,marginLeft,areaWidth,areaHeight,?,activeArea,?,imageSize,draggingArea"
     * Units are in millimeters, converted to pixels using DPI later.
     */
    private void readSensorAreaFromSysFs() {
        try {
            File posFile = new File(SYSFS_FP_POSITION);
            if (!posFile.exists()) {
                Log.w(TAG, "Sensor position sysfs not found: " + SYSFS_FP_POSITION);
                return;
            }

            byte[] data = java.nio.file.Files.readAllBytes(posFile.toPath());
            String raw = new String(data, StandardCharsets.UTF_8).trim();
            String[] parts = raw.split(",");

            if (parts.length >= 9) {
                mSensorMarginBottom = Double.parseDouble(parts[0]);
                mSensorMarginLeft = Double.parseDouble(parts[1]);
                mSensorAreaWidth = Double.parseDouble(parts[2]);
                mSensorAreaHeight = Double.parseDouble(parts[3]);
                mSensorActiveArea = Double.parseDouble(parts[5]);
                Log.i(TAG, "Sensor position: " + mSensorAreaWidth + "x" + mSensorAreaHeight
                        + " margin=" + mSensorMarginBottom + "," + mSensorMarginLeft
                        + " active=" + mSensorActiveArea);
            } else {
                Log.w(TAG, "Unexpected sensor position format (" + parts.length
                        + " fields): " + raw);
            }
        } catch (Exception e) {
            Log.w(TAG, "readSensorAreaFromSysFs failed", e);
        }
    }

    /**
     * Calculate the FOD sensor rectangle in screen pixels and send it to the
     * touchscreen controller via the SysInput HAL. This tells the TSP exactly
     * where the ultrasonic fingerprint sensor is located on the display.
     *
     * <p>The sensor position values from sysfs are in millimeters. We convert
     * them to pixels using: {@code mm * xdpi * (1 inch / 25.4 mm)}.
     */
    private void setFodRect() {
        if (mSysInputHal == null) {
            Log.w(TAG, "HAL not connected, cannot send FOD rect");
            return;
        }

        try {
            // Get screen size from WindowManager
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            if (wm == null) {
                Log.w(TAG, "WindowManagerService not available");
                return;
            }

            android.graphics.Point screenSize = new android.graphics.Point();
            wm.getInitialDisplaySize(Display.DEFAULT_DISPLAY, screenSize);

            float xdpi = getResources().getDisplayMetrics().xdpi;
            // mm to pixels: value_mm * dpi * (1 inch / 25.4 mm)
            double mmToPx = xdpi / 25.4;

            int activeArea = (int) (mSensorActiveArea * mmToPx);
            int marginBottom = (int) (mSensorMarginBottom * mmToPx);
            int marginLeft = (int) (mSensorMarginLeft * mmToPx);
            int areaHeight = (int) (mSensorAreaHeight * mmToPx);

            int rectSize = activeArea;
            int left = (screenSize.x / 2) - (rectSize / 2) + marginLeft;
            int top = screenSize.y - marginBottom - (areaHeight / 2) - (rectSize / 2);
            int right = left + rectSize;
            int bottom = top + rectSize;

            String command = SET_FOD_RECT + "," + left + "," + top + "," + right + "," + bottom;

            int ret = mSysInputHal.setProperty(TYPE_DEFAULT_TSP, PROPERTY_CMD, command);
            Log.i(TAG, "FOD rect: [" + left + "," + top + "," + right + "," + bottom
                    + "] screen=" + screenSize.x + "x" + screenSize.y
                    + " dpi=" + xdpi + " ret=" + ret);
            mFodRectSent = true;

        } catch (Exception e) {
            Log.e(TAG, "setFodRect failed", e);
        }
    }

    // ========================================================================
    // Foreground notification (minimal, required for Android 14+)
    // ========================================================================

    private void startForegroundNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Biometric Service",
                NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Samsung Biometrics")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }
}
