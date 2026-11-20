/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Monitors logical display brightness via {@link DisplayManager#EVENT_TYPE_DISPLAY_BRIGHTNESS}
 * and maps AOSP's normalized {@code [0.0f..1.0f]} brightness to the device's panel backlight
 * scale ({@code [0..maxBacklight]}), with sysfs fallback when needed.
 */
public final class DisplayBrightnessMonitor {
    private static final String TAG = "SB_BrightnessMonitor";

    public interface OnBrightnessListener {
        void onBrightnessChanged(int brightness);
    }

    private static final class InstanceHolder {
        private static final DisplayBrightnessMonitor INSTANCE =
                new DisplayBrightnessMonitor(Looper.getMainLooper());
    }

    public static DisplayBrightnessMonitor getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private final Handler mHandler;
    private final String mBrightnessFilePath;
    private final List<OnBrightnessListener> mListeners = new CopyOnWriteArrayList<>();

    private Context mContext;
    private DisplayManager mDisplayManager;
    private ContentObserver mContentObserver;
    private boolean mDisplayListenerRegistered;

    private volatile float mMaxBacklight = 255.0f;
    private volatile float mCurrentBrightnessFloat = -1.0f;
    private volatile int mCurrentBrightness = -1;

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        refreshBrightness();
                    }
                }
            };

    public DisplayBrightnessMonitor(Looper looper) {
        mHandler = new Handler(looper);
        if ("qcom".equals(Build.HARDWARE)) {
            mBrightnessFilePath = "/sys/class/backlight/panel0-backlight/brightness";
        } else {
            mBrightnessFilePath = "/sys/class/lcd/panel/device/backlight/panel/brightness";
        }
    }

    public void init(Context context) {
        if (mContext != null || context == null) return;
        mContext = context.getApplicationContext();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);

        mContentObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                refreshBrightness();
            }
        };

        try {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                    false,
                    mContentObserver);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("screen_brightness_float"),
                    false,
                    mContentObserver);
        } catch (Exception e) {
            Log.w(TAG, "Failed to register brightness content observer", e);
        }

        refreshBrightness();
    }

    public void setMaxBacklight(float maxBacklight) {
        if (maxBacklight > 0.0f && mMaxBacklight != maxBacklight) {
            mMaxBacklight = maxBacklight;
            refreshBrightness();
        }
    }

    public void registerListener(OnBrightnessListener listener) {
        if (listener == null) return;
        if (mListeners.contains(listener)) return;

        mListeners.add(listener);
        if (mListeners.size() == 1) {
            observe(true);
        }
    }

    public void unregisterListener(OnBrightnessListener listener) {
        if (listener == null) return;
        if (!mListeners.contains(listener)) return;

        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            mHandler.removeCallbacksAndMessages(null);
            observe(false);
        }
    }

    private void observe(boolean enable) {
        if (mDisplayManager == null) return;
        if (enable) {
            if (!mDisplayListenerRegistered) {
                try {
                    mDisplayManager.registerDisplayListener(
                            mDisplayListener, mHandler,
                            DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS);
                    mDisplayListenerRegistered = true;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to register DisplayManager brightness listener", e);
                }
            }
            refreshBrightness();
        } else {
            if (mDisplayListenerRegistered) {
                try {
                    mDisplayManager.unregisterDisplayListener(mDisplayListener);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to unregister DisplayManager brightness listener", e);
                }
                mDisplayListenerRegistered = false;
            }
        }
    }

    public void refreshBrightness() {
        int mapped = queryBrightness();
        if (mapped >= 0) {
            postBrightnessChanged(mapped);
        }
    }

    public void postBrightnessChanged(int brightness) {
        mHandler.post(() -> handleDisplayBrightnessChanged(brightness));
    }

    private void handleDisplayBrightnessChanged(int newValue) {
        if (mCurrentBrightness == newValue) return;
        mCurrentBrightness = newValue;

        for (OnBrightnessListener l : mListeners) {
            try {
                l.onBrightnessChanged(mCurrentBrightness);
            } catch (Throwable t) {
                Log.w(TAG, "Listener threw: " + t.getMessage());
            }
        }
    }

    /**
     * Queries current logical brightness mapped onto {@code [0..mMaxBacklight]}.
     * Uses {@link DisplayManager#getBrightness(int)} first (SELinux-safe, unaffected by HBM
     * backlight overrides), falling back to sysfs if unavailable.
     */
    private int queryBrightness() {
        if (mDisplayManager != null) {
            try {
                float norm = mDisplayManager.getBrightness(Display.DEFAULT_DISPLAY);
                if (!Float.isNaN(norm) && norm >= 0.0f) {
                    float clamped = Math.min(1.0f, norm);
                    mCurrentBrightnessFloat = clamped;
                    return Math.round(clamped * mMaxBacklight);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "DisplayManager.getBrightness failed, falling back to sysfs", e);
            }
        }

        int sysfsVal = getBrightnessFromFile();
        if (sysfsVal >= 0) {
            mCurrentBrightnessFloat = Math.min(1.0f, sysfsVal / Math.max(1.0f, mMaxBacklight));
        }
        return sysfsVal;
    }

    public int getCurrentBrightness() {
        int now = queryBrightness();
        if (now >= 0) {
            mCurrentBrightness = now;
        }
        return mCurrentBrightness;
    }

    public float getCurrentBrightnessFloat() {
        queryBrightness();
        return mCurrentBrightnessFloat;
    }

    public int getBrightnessFromFile() {
        byte[] data;
        try {
            data = Utils.readFile(new File(mBrightnessFilePath));
        } catch (IOException e) {
            Log.w(TAG, "Failed to read brightness file", e);
            return -1;
        }
        if (data == null || data.length == 0) return -1;

        try {
            return Integer.parseInt(new String(data, StandardCharsets.UTF_8).trim());
        } catch (NumberFormatException e) {
            Log.w(TAG, "getBrightnessFromFile: " + e.getMessage());
            return -1;
        }
    }

    public String getBrightnessFilePath() {
        return mBrightnessFilePath;
    }
}
