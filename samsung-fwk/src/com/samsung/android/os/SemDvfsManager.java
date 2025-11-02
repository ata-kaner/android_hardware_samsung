/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.samsung.android.os;

import android.content.Context;

public class SemDvfsManager {
    private static final SemDvfsManager sStub = new SemDvfsManager();

    protected SemDvfsManager() {}

    public static SemDvfsManager createInstance(Context context, String tagName) {
        return sStub;
    }

    public static SemDvfsManager createInstance(Context context, String tagName, int type) {
        return sStub;
    }

    public boolean checkHintSupported(int hint) {
        return false;
    }

    public void setHint(int hint) {}

    public void acquire() {}

    public void acquire(int timeout) {}

    public void acquire(String extra) {}

    public void release() {}
}
