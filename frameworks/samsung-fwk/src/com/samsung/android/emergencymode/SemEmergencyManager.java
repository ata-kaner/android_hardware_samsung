package com.samsung.android.emergencymode;

import android.content.Context;
import android.os.Handler;

/**
 * AOSP/Lineage stub for Samsung SemEmergencyManager.
 *
 * Samsung's emergency mode is proprietary (service + framework hooks).
 * On non-Samsung builds, treat it as unsupported and always "off".
 */
public class SemEmergencyManager {
    private static final Object mLock = new Object();
    private static SemEmergencyManager sInstance;

    private final Context mContext;
    @SuppressWarnings("unused")
    private final Handler mHandler;

    private static boolean mIsLoadedFeatures = true;
    private static boolean EMERGENCY_FEATURES_SUPPORTED = false;

    @Deprecated
    public static SemEmergencyManager getInstance(Context context) {
        if (context == null) return null;
        synchronized (mLock) {
            if (sInstance == null) {
                sInstance = new SemEmergencyManager(new Handler(context.getMainLooper()), context);
            }
            return sInstance;
        }
    }

    private SemEmergencyManager(Handler handler, Context context) {
        mHandler = handler;
        mContext = context;
    }

    public static boolean isEmergencyFeaturesSupported() {
        // Always false on stub
        return false;
    }

    public void readyEmergencyMode() {
        // no-op
    }

    @Deprecated
    public static boolean isEmergencyMode(Context context) {
        return false;
    }

    public static boolean isMinimalBatteryUseMode(Context context) {
        return false;
    }

    public static boolean isBatteryConservingMode(Context context) {
        return false;
    }

    @Deprecated
    public static boolean isUltraPowerSavingModeSupported() {
        return false;
    }

    public static boolean isBatteryConversingModeSupported() {
        return false;
    }

    public boolean isEmergencyMode() {
        return false;
    }

    /**
     * Samsung: 0 emergency, 1 UPSM, 2 BCM, 3 MPSM, -1 normal (roughly).
     * Stub: always normal.
     */
    public int getModeType() {
        return -1;
    }

    public int getEmergencyState() {
        return -1;
    }

    public boolean checkValidIntentAction(String action, String packageName) {
        // If emergency is unsupported, allow
        return true;
    }

    public boolean checkInvalidProcess(String processName) {
        return false;
    }

    public boolean checkInvalidBroadcast(String action, String senderPkg) {
        return false;
    }

    public boolean needMobileDataBlock() {
        return false;
    }

    public boolean isScreenOn() {
        return false;
    }

    public void setUserPackageBlocked(boolean blocked, Context context) {
        // no-op
    }

    public boolean isUserPackageBlocked() {
        return false;
    }

    public boolean isModifying() {
        return false;
    }

    public boolean canSetMode() {
        // Since we don't support EM, caller shouldn't be allowed to set it.
        return false;
    }

    @Deprecated
    public boolean checkModeType(int modeType) {
        return false;
    }
}
