package com.samsung.android.emergencymode;

public final class SemEmergencyConstants {
    private SemEmergencyConstants() {}

    public static final String SERVICE_NAME = "emergency_manager";

    public static final String EMERGENCY_START_SERVICE_BY_ORDER =
            "com.samsung.android.emergencymode.action.START_SERVICE_BY_ORDER";
    public static final String EMERGENCY_START_SERVICE_BY_ORDER_OLD =
            "com.samsung.android.emergencymode.action.START_SERVICE_BY_ORDER_OLD";
    public static final String EMERGENCY_CHECK_ABNORMAL_STATE =
            "com.samsung.android.emergencymode.action.CHECK_ABNORMAL_STATE";

    public static final String EXTRA_EMERGENCY_START_SERVICE_FLAG =
            "extra_emergency_start_service_flag";
    public static final String EXTRA_EMERGENCY_START_SERVICE_SKIPDIALOG =
            "extra_emergency_start_service_skipdialog";
    public static final String EXTRA_CLEAR_BOOT_TIME =
            "extra_clear_boot_time";
    public static final String EXTRA_INIT_FOR_EM_STATE =
            "extra_init_for_em_state";

    public static final String EMERGENCY_LAUNCHER = "com.sec.android.emergencylauncher";
    public static final String EMERGENCY_LAUNCHER_CLASS =
            "com.sec.android.emergencylauncher.launcher.LauncherActivity";
    public static final String EMERGENCY_SERVICE_STARTER =
            "com.sec.android.emergencymode.service.EmergencyServiceStarter";
}
