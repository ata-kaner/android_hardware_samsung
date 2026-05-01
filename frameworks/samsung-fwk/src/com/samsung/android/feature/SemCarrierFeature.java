package com.samsung.android.feature;

/**
 * Stub implementation of Samsung's SemCarrierFeature for AOSP/Lineage builds.
 *
 * Behavior:
 * - Always returns the provided default values (or null for array variants when no default exists).
 * - Keeps the same API surface so Samsung prebuilts can link.
 */
public class SemCarrierFeature {

    private static final SemCarrierFeature INSTANCE = new SemCarrierFeature();

    // Keep these fields because some prebuilts may reference them (rare, but safe)
    private static final String LOG_TAG = "SemCarrierFeature";
    static final boolean DEBUG = false;
    static final boolean TEST = false;

    private SemCarrierFeature() {}

    public static SemCarrierFeature getInstance() {
        return INSTANCE;
    }

    /**
     * Samsung keeps this for legacy. We'll just return the singleton.
     */
    @Deprecated(forRemoval = true, since = "16.0")
    public static SemCarrierFeature createInstance() {
        return INSTANCE;
    }

    /**
     * On Samsung this returns canonical/carrier ID. We don't have that on AOSP.
     */
    public int getCarrierId(int phoneId, boolean last) {
        return 0;
    }

    public boolean getBoolean(int phoneId, String key, boolean defValue, boolean checkLastSim) {
        return defValue;
    }

    public boolean[] getBooleanArray(int phoneId, String key, boolean checkLastSim) {
        return null;
    }

    public int getInt(int phoneId, String key, int defValue, boolean checkLastSim) {
        return defValue;
    }

    public int[] getIntArray(int phoneId, String key, boolean checkLastSim) {
        return null;
    }

    public long getLong(int phoneId, String key, long defValue, boolean checkLastSim) {
        return defValue;
    }

    public long[] getLongArray(int phoneId, String key, boolean checkLastSim) {
        return null;
    }

    public double getDouble(int phoneId, String key, double defValue, boolean checkLastSim) {
        return defValue;
    }

    public double[] getDoubleArray(int phoneId, String key, boolean checkLastSim) {
        return null;
    }

    public String getString(int phoneId, String key, String defValue, boolean checkLastSim) {
        return defValue;
    }

    public String[] getStringArray(int phoneId, String key, boolean checkLastSim) {
        return null;
    }
}
