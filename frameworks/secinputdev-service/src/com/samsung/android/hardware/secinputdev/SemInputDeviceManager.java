package com.samsung.android.hardware.secinputdev;

import android.util.Log;
import com.samsung.android.hardware.secinputdev.utils.SemInputConstants;

public class SemInputDeviceManager {
    public static final int DEVID_DEFAULT_TSP = 1;
    public static final int DEVID_EXTRA_TSP = 2;
    public static final int DEVID_KEY = 21;
    public static final int DEVID_KEYBOARD = 31;
    public static final int DEVID_SPEN = 11;
    public static final int DEVID_TSP_MAX = 3;
    public static final int FORCE_OFF = 21;
    public static final int FORCE_ON = 22;
    public static final int KEY_APPSELECT = 580;
    public static final int KEY_BACK = 158;
    public static final int KEY_EMERGENCY = 672;
    public static final int KEY_HOME = 172;
    public static final int KEY_HOT = 252;
    public static final int KEY_MICMUTE = 248;
    public static final int KEY_POWER = 116;
    public static final int KEY_RECENT = 254;
    public static final int KEY_VOLUMEDOWN = 114;
    public static final int KEY_VOLUMEUP = 115;
    public static final int MODE_DISABLE = 0;
    public static final int MODE_ENABLE = 1;
    public static final int RESULT_NG = -1;
    public static final int RESULT_OK = 0;
    public static final String RESULT_STR_NA = "NA";
    public static final String RESULT_STR_NG = "NG";
    public static final int SUPPORT_AOT = 1;
    public static final int SUPPORT_INPUT_MONITOR = 65536;
    public static final int SUPPORT_MISCALIBRATION = 512;
    public static final int SUPPORT_MULTICALIBRATION = 1024;
    public static final int SUPPORT_OPENSHORT = 256;
    public static final int SUPPORT_PRESSURE = 2;
    public static final int SUPPORT_PROX_LP_SCAN_ENABLED = 64;
    public static final int SUPPORT_RAWDATA_TRANSFER = 262144;
    public static final int SUPPORT_RR120 = 4;
    public static final int SUPPORT_SYSINPUT_ENABLED = 32;
    public static final int SUPPORT_VRR = 8;
    public static final int SUPPORT_WIRELESS_TX = 16;
    private static final String TAG = "SemInputDeviceManager";
    public static int gloveMode = 0;
    private ISemInputDeviceManager service;

    public SemInputDeviceManager(ISemInputDeviceManager service) {
        if (service == null) {
            Log.d(TAG, "ISemInputDeviceManager is null");
        } else {
            Log.d(TAG, "SemInputDeviceManager ++");
            this.service = service;
        }
    }

    public int getSupportDevice(int devid) {
        if (this.service == null) {
            Log.e(TAG, "getSupportDevice: service is not enabled");
            return -1;
        }
        try {
            return this.service.getSupportDevice(SemInputConstants.Device.getFromInt(devid));
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return -1;
        }
    }

    private int activate(SemInputConstants.Device device, SemInputConstants.DisplayState mode, boolean state) {
        if (this.service == null) {
            Log.e(TAG, "activate: service is not enabled");
            return -1;
        }
        Log.d(TAG, "activate: " + device + " " + mode + "," + state);
        try {
            return this.service.activate(device, mode, state);
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return -1;
        }
    }

    private int setProperty(SemInputConstants.Device device, SemInputConstants.Command command, String mode) {
        if (this.service == null) {
            Log.e(TAG, "setProperty: service is not enabled");
            return -1;
        }
        Log.d(TAG, "setProperty: " + device + " " + SemInputConstants.Property.CMD + "," + command + "," + mode);
        try {
            return this.service.setCommand(device, command, mode);
        } catch (Exception e) {
            Log.e(TAG, "setProperty: Failed to call interface: ", e);
            return -1;
        }
    }

    private int setProperty(SemInputConstants.Command command, String mode) {
        return setProperty(SemInputConstants.Device.NOT_SPECIFIED, command, mode);
    }

    private int setProperty(SemInputConstants.Device device, SemInputConstants.Property property, String mode) {
        if (this.service == null) {
            Log.e(TAG, "setProperty: service is not enabled");
            return -1;
        }
        Log.d(TAG, "setProperty: " + device + " " + property + "," + mode);
        try {
            return this.service.setProperty(device, property, mode);
        } catch (Exception e) {
            Log.e(TAG, "setProperty: Failed to call interface: ", e);
            return -1;
        }
    }

    private String getProperty(SemInputConstants.Device device, SemInputConstants.Property property) {
        if (this.service == null) {
            Log.e(TAG, "getProperty: service is not enabled");
            return "NG";
        }
        Log.d(TAG, "getProperty: " + device + " " + property);
        try {
            return this.service.getProperty(device, property);
        } catch (Exception e) {
            Log.e(TAG, "getProperty: Failed to call interface: ", e);
            return "NG";
        }
    }

    public String getCommandList(int devid) {
        if (this.service == null) {
            Log.e(TAG, "getCommandList: service is not enabled");
            return "NG";
        }
        try {
            return this.service.getCommandList(SemInputConstants.Device.getFromInt(devid));
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return "NG";
        }
    }

    private String runCommand(SemInputConstants.Device device, String cmd) {
        if (this.service == null) {
            Log.e(TAG, "runCommand: service is not enabled");
            return "NG";
        }
        Log.d(TAG, "runCommand: " + device + " " + cmd);
        try {
            return this.service.runCommand(device, cmd);
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return "NG";
        }
    }

    public String runEmergency(int devid, String cmd) {
        return runCommand(SemInputConstants.Device.getFromInt(devid), cmd);
    }

    public String runEmergencyCurrentTsp(String cmd) {
        return runCommand(SemInputConstants.Device.CURRENT_TSP, cmd);
    }

    public String getKeyPressStateAll() {
        if (this.service == null) {
            Log.e(TAG, "getKeyPressStateAll: service is not enabled");
            return "";
        }
        try {
            return this.service.getKeyPressStateAll();
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return "";
        }
    }

    public boolean isKeyPressedByKeycode(int keycode) {
        if (this.service == null) {
            Log.e(TAG, "isKeyPressedByKeycode: service is not enabled");
            return false;
        }
        try {
            return this.service.isKeyPressedByKeycode(keycode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return false;
        }
    }

    public int setTspEnabled(int devid, int mode, boolean state) {
        return activate(SemInputConstants.Device.getFromInt(devid), SemInputConstants.DisplayState.getFromInt(mode), state);
    }

    public int setGripData(String mode) {
        return setProperty(SemInputConstants.Device.CURRENT_TSP, SemInputConstants.Command.GRIP_DATA, mode);
    }

    public int setSipMode(int mode) {
        return setProperty(SemInputConstants.Command.SIP, mode + "");
    }

    public int setNoteMode(int mode) {
        return setProperty(SemInputConstants.Command.NOTE_APP, mode + "");
    }

    public int setTemperature(int value) {
        return setProperty(SemInputConstants.Command.TEMPERATURE, value + "");
    }

    public int setSpayEnable(int mode) {
        return setProperty(SemInputConstants.Command.SPAY, mode + "");
    }

    public int setStylusEnable(int mode) {
        return setProperty(SemInputConstants.Command.STYLUS, mode + "");
    }

    public int setBrushEnable(int mode) {
        return setProperty(SemInputConstants.Command.BRUSH, mode + "");
    }

    public int setAodRect(int w, int h, int x, int y) {
        return setProperty(SemInputConstants.Command.AOD_RECT, w + "," + h + "," + x + "," + y);
    }

    public int setAodNotiRect(int w, int h, int x, int y) {
        return setProperty(SemInputConstants.Command.AOD_NOTI_RECT, w + "," + h + "," + x + "," + y);
    }

    public int setAodEnable(int mode) {
        return setProperty(SemInputConstants.Command.AOD, mode + "");
    }

    public int setAotEnable(int mode) {
        if (this.service == null) {
            Log.e(TAG, "setAotEnable: service is not enabled");
            return -1;
        }
        Log.d(TAG, "setAotEnable: " + mode);
        try {
            return this.service.setAotEnable(mode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return -1;
        }
    }

    public int setFodEnable(int mode, int pressFast, int strictMode, int control) {
        if (mode == 1) {
            return setProperty(SemInputConstants.Command.FOD, mode + "," + pressFast + "," + strictMode + "," + control);
        }
        return setProperty(SemInputConstants.Command.FOD, mode + "");
    }

    public int setFodIconVisible(int mode) {
        return setProperty(SemInputConstants.Command.FOD_ICON_VISIBLE, mode + "");
    }

    public int setFodRect(int left, int top, int right, int bottom) {
        return setProperty(SemInputConstants.Device.CURRENT_TSP, SemInputConstants.Command.FOD_RECT, left + "," + top + "," + right + "," + bottom);
    }

    public int setFodLpMode(int mode) {
        return setProperty(SemInputConstants.Command.FOD_LP, mode + "");
    }

    public int setSingletapEnable(int mode) {
        return setProperty(SemInputConstants.Command.SINGLETAP, mode + "");
    }

    public int setTouchableArea(int mode) {
        return setProperty(SemInputConstants.Command.TOUCHABLE_AREA, mode + "");
    }

    public int setSyncChanged(int mode) {
        return setProperty(SemInputConstants.Command.SYNC_CHANGED, mode + "");
    }

    public int setPocketModeEnable(int mode) {
        return setProperty(SemInputConstants.Command.POCKET_MODE, mode + "");
    }

    public int setLowSensitivityModeEnable(int mode) {
        return setProperty(SemInputConstants.Command.LOW_SENSITIVITY, mode + "");
    }

    public int setLowSensitivityMode(int mode, int level) {
        return setProperty(SemInputConstants.Command.LOW_SENSITIVITY, mode + "," + level);
    }

    public int setAlwaysLowPowerMode(int devid, int enable) {
        return setProperty(SemInputConstants.Device.getFromInt(devid), SemInputConstants.Command.ALWAYS_LOW_POWER_MODE, enable + "");
    }

    public int getTspSupportFeature(int devid) {
        if (this.service == null) {
            Log.e(TAG, "getTspSupportFeature: service is not enabled");
            return 0;
        }
        try {
            return this.service.getTspSupportFeature(SemInputConstants.Device.getFromInt(devid));
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return 0;
        }
    }

    public String getScrubPosition(int devid) {
        return getProperty(SemInputConstants.Device.getFromInt(devid), SemInputConstants.Property.SCRUB_POS);
    }

    public int setProxPowerOff(int devid, int mode) {
        return setProperty(SemInputConstants.Device.getFromInt(devid), SemInputConstants.Property.PROX_OFF, mode + "");
    }

    public int setWirelessChargingMode(int devid, int mode) {
        if (devid == 1) {
            return setProperty(SemInputConstants.Device.NOT_SPECIFIED, SemInputConstants.Command.WIRELESS_CHARGER, mode + "");
        }
        return setProperty(SemInputConstants.Device.getFromInt(devid), SemInputConstants.Command.WIRELESS_CHARGER, mode + "");
    }

    public int setWirelessChargingFrequency(int frequency) {
        return setProperty(SemInputConstants.Device.NOT_SPECIFIED, SemInputConstants.Command.WIRELESS_CHARGING_FREQ, frequency + "");
    }

    public void setCoverMode(boolean switchState, int coverType) {
        if (switchState) {
            setProperty(SemInputConstants.Device.DEFAULT_TSP, SemInputConstants.Command.CLEAR_COVER, gloveMode + "");
            setProperty(SemInputConstants.Device.EXTRA_TSP, SemInputConstants.Command.CLEAR_COVER, gloveMode + "");
            setProperty(SemInputConstants.Device.SPEN, SemInputConstants.Command.CLEAR_COVER, "0," + coverType);
            return;
        }
        setProperty(SemInputConstants.Device.NOT_SPECIFIED, SemInputConstants.Command.CLEAR_COVER, "3," + coverType);
    }

    public String getFodInfo(int devid) {
        return getProperty(SemInputConstants.Device.getFromInt(devid), SemInputConstants.Property.FOD_INFO);
    }

    public String getFodPosition(int devid) {
        return getProperty(SemInputConstants.Device.getFromInt(devid), SemInputConstants.Property.FOD_POS);
    }

    public String getAodActiveArea(int devid) {
        return getProperty(SemInputConstants.Device.getFromInt(devid), SemInputConstants.Property.AOD_ACTIVE_AREA);
    }

    public int setSpenEnabled(int devid, int mode, boolean state) {
        return activate(SemInputConstants.Device.getFromInt(devid), SemInputConstants.DisplayState.getFromInt(mode), state);
    }

    public int setSpenCoverType(int type) {
        return setProperty(SemInputConstants.Device.SPEN, SemInputConstants.Command.SPEN_COVER_TYPE, type + "");
    }

    public String getSpenPosition() {
        return getProperty(SemInputConstants.Device.SPEN, SemInputConstants.Property.EPEN_POS);
    }

    public int setSpenPower(int mode) {
        return setProperty(SemInputConstants.Device.SPEN, SemInputConstants.Command.SPEN_POWER, mode + "");
    }

    public int setSpenBleChargeMode(int mode) {
        return setProperty(SemInputConstants.Device.SPEN, SemInputConstants.Command.SPEN_BLE_CHARGING, mode + "");
    }

    public int setSpenPdctLowSensitivityEnable(int mode) {
        return setProperty(SemInputConstants.Device.SPEN, SemInputConstants.Command.SPEN_PDCT_LOWSENSITIVITY, mode + "");
    }

    public int setSpenLowCurrentMode(int mode) {
        return setProperty(SemInputConstants.Device.SPEN, SemInputConstants.Command.SPEN_LOWCURRENT, mode + "");
    }

    public int setSpenPowerSavingMode(int mode) {
        return setProperty(SemInputConstants.Device.SPEN, SemInputConstants.Command.SPEN_SAVING_MODE, mode + "");
    }

    public int getDeviceEnabled(int devid) {
        if (this.service == null) {
            Log.e(TAG, "getDeviceEnabled: service is not enabled");
            return -1;
        }
        try {
            return this.service.getDeviceEnabled(SemInputConstants.Device.getFromInt(devid));
        } catch (Exception e) {
            Log.e(TAG, "Failed to call interface: ", e);
            return -1;
        }
    }
}
