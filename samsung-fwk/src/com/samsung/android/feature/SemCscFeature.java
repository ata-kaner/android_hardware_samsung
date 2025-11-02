/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.samsung.android.feature;

public final class SemCscFeature {
    private static final SemCscFeature sInstance = new SemCscFeature();

    private SemCscFeature() {}

    public static SemCscFeature getInstance() {
        return sInstance;
    }

    public String getString(String tag) {
        return "";
    }

    public String getString(String tag, String defaultValue) {
        return defaultValue;
    }

    public boolean getBoolean(String tag) {
        return false;
    }

    public boolean getBoolean(String tag, boolean defaultValue) {
        return defaultValue;
    }

    public int getInt(String tag) {
        return -1;
    }

    public int getInt(String tag, int defaultValue) {
        return defaultValue;
    }
}
