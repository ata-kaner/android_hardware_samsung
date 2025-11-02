/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.samsung.android.feature;

public interface IFloatingFeature {
    boolean getBoolean(String tag);
    boolean getBoolean(String tag, boolean defaultValue);
    int getInt(String tag);
    int getInt(String tag, int defaultValue);
    String getString(String tag);
    String getString(String tag, String defaultValue);
}
