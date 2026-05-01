package com.samsung.android.service.EngineeringMode;

import android.content.Context;

/**
 * AOSP/Lineage stub for Samsung EngineeringModeManager.
 *
 * This API is Samsung-specific and relies on proprietary JNI/service.
 * On non-Samsung frameworks we report "not connected" and return error codes.
 */
public final class EngineeringModeManager {
    public static final int ALLOWED = 1;
    public static final int NOT_ALLOWED = 0;

    public static final int OK = 1;
    public static final int NOK = 0;

    public static final int ENABLE = 0;
    public static final int DISABLE = 1;

    public static final int ERROR_EM_SERVICE = -1000;
    public static final int ERROR_NO_PERMISSION = -1300;
    public static final int ERROR_NOT_SUPPORTED = -1600;
    public static final int ERROR_INVALID_PARAM = -1700;

    // Some callers may compare against these constants
    public static final String ERRORSTRING_EM_SERVICE = "ERROR_EM_SERVICE";
    public static final String ERRORSTRING_INTERNAL = "ERROR_INTERNAL";
    public static final String ERRORSTRING_NOT_INSTALLED = "ERROR_TOKEN_NOT_INSATLLED";
    public static final String ERRORSTRING_NO_PERMISSION = "ERROR_NO_PERMISSION";

    public static final byte[] ERRORBYTE_EM_SERVICE = new byte[] { (byte) 0xFF }; // matches {-1} intent
    public static final byte[] ERRORBYTE_NO_PERMISSION = null;
    public static final byte[] ERRORBYTE_NOT_SUPPORTED = new byte[] { (byte) 0xFE }; // matches {-2}
    public static final byte[] ERRORBYTE_INVAILD_PARAM = new byte[] { (byte) 0xFD }; // matches {-3}
    public static final byte[] ERRORBYTE_NOT_INSATALLED = new byte[] { (byte) 0xFC }; // matches {-4}

    private final Context mContext;

    public EngineeringModeManager(Context context) {
        mContext = context;
    }

    /**
     * On Samsung this indicates whether the proprietary backend is available.
     * Stubbed to false.
     */
    public boolean isConnected() {
        return false;
    }

    /**
     * On Samsung returns 1 when allowed/active for a given mode.
     * Stubbed to error so callers will treat as "not activated".
     */
    public int getStatus(int mode) {
        return ERROR_EM_SERVICE;
    }

    // ---- Optional API surface (only add if your prebuilts reference them) ----

    public byte[] getRequestMsg(String a, String b, byte[] c) {
        return ERRORBYTE_EM_SERVICE;
    }

    public byte[] getRequestMsg(String a, String b, byte[] c, int d) {
        return ERRORBYTE_EM_SERVICE;
    }

    public int installToken(byte[] token) {
        return ERROR_EM_SERVICE;
    }

    public int isTokenInstalled() {
        return ERROR_EM_SERVICE;
    }

    public int removeToken() {
        return ERROR_EM_SERVICE;
    }

    public byte[] getID() {
        return ERRORBYTE_EM_SERVICE;
    }

    public String getExpiryDate() {
        return null;
    }

    public int getNumOfModes() {
        return ERROR_EM_SERVICE;
    }

    public int sendFuseCmd() {
        return ERROR_EM_SERVICE;
    }

    public byte[] makeITLReq(String a, String b) {
        return ERRORBYTE_EM_SERVICE;
    }

    public int recoveryITL(byte[] data) {
        return ERROR_EM_SERVICE;
    }

    public byte[] makeTokenReq(String a, String b, byte[] data, String c) {
        return ERRORBYTE_EM_SERVICE;
    }

    public byte[] essCommand(String cmd) {
        return ERRORBYTE_EM_SERVICE;
    }

    public long getServerTime() {
        return ERROR_EM_SERVICE;
    }

    public int getTUC(int mode) {
        return ERROR_EM_SERVICE;
    }

    public int[] getModes() {
        return new int[] { ERROR_EM_SERVICE };
    }

    public String getStringModes() {
        return ERRORSTRING_EM_SERVICE;
    }

    public String getLastTokenStatus() {
        return ERRORSTRING_EM_SERVICE;
    }

    public byte[] makeTimeReq() {
        return null;
    }

    public byte[] updateTime(byte[] data) {
        return null;
    }
}
