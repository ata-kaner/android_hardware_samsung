package com.samsung.android.feature;

import android.os.SystemProperties;

/**
 * AOSP stub for Samsung CSC feature API.
 *
 * Real Samsung CSC reads carrier/OEM feature XML. On AOSP we usually don't have it,
 * so expose safe defaults.
 */
public final class SemCscFeature {
    private static final SemCscFeature sInstance = new SemCscFeature();

    private SemCscFeature() {}

    public static SemCscFeature getInstance() {
        return sInstance;
    }

    /**
     * Samsung API: returns string for a CSC feature key.
     * IMPORTANT: must never return null (callers often do .toUpperCase()).
     */
    public String getString(String key) {
        // Optional: let you override via a property, useful for testing.
        // e.g. setprop persist.sys.csc.CscFeature_Contact_ConfigImsOpStyle foo
        String v = SystemProperties.get("persist.sys.csc." + key, "");
        return v != null ? v : "";
    }

    public String getString(String key, String def) {
        String v = getString(key);
        return (v.isEmpty() ? (def != null ? def : "") : v);
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean def) {
        String v = SystemProperties.get("persist.sys.csc." + key, "");
        if (v == null || v.isEmpty()) return def;
        return Boolean.parseBoolean(v);
    }

    public int getInt(String key) {
        return getInt(key, -1);
    }

    public int getInt(String key, int def) {
        String v = SystemProperties.get("persist.sys.csc." + key, "");
        if (v == null || v.isEmpty()) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // Some Samsung variants also expose these overloads. Cheap to include:
    public boolean getBoolean(int phoneId, String key) { return getBoolean(key); }
    public boolean getBoolean(int phoneId, String key, boolean def) { return getBoolean(key, def); }
    public String getString(int phoneId, String key) { return getString(key); }
    public String getString(int phoneId, String key, String def) { return getString(key, def); }
    public int getInt(int phoneId, String key) { return getInt(key); }
    public int getInt(int phoneId, String key, int def) { return getInt(key, def); }
}