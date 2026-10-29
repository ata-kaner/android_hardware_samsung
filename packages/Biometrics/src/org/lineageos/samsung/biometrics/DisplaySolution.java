/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.content.Context;
import android.util.Log;

public class DisplaySolution {

    private static final String TAG = "SB_DisplaySolution";

    private static final float DEFAULT_UDFPS_BRIGHTNESS = 319.0f;

    private final String[] mBrightnessBacklightValueStringArray;
    private final String[] mBrightnessNitsValueStringArray;
    private final String[] mGammaStringArray;
    private final float[] mGammaArray;

    public DisplaySolution(Context context) {
        mBrightnessBacklightValueStringArray = context.getResources().getStringArray(
                R.array.config_Screen_Brightness_Backlight_Value);
        mBrightnessNitsValueStringArray = context.getResources().getStringArray(
                R.array.config_Screen_Brightness_Nits_Value);
        mGammaStringArray = context.getResources().getStringArray(
                R.array.config_Display_Solution_Gamma_Value);

        if (mGammaStringArray != null && mGammaStringArray.length > 0) {
            mGammaArray = new float[mGammaStringArray.length];
            for (int i = 0; i < mGammaStringArray.length; i++) {
                try {
                    mGammaArray[i] = Float.parseFloat(mGammaStringArray[i]);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse gamma value: " + mGammaStringArray[i]);
                    mGammaArray[i] = 2.2f;
                }
            }
        } else {
            mGammaArray = new float[]{2.2f};
        }
    }

    public float getAlphaMaskLevel(float currentBrightnessIndex, float fingerPrintIndex, float brCtrl) {
        return calculateAlphaBlendingValue(Math.round(currentBrightnessIndex), fingerPrintIndex, brCtrl);
    }

    public int getAlphaMaskLevel(int currentBrightnessIndex, float fingerPrintIndex) {
        float alphaBlendingValue = calculateAlphaBlendingValue(currentBrightnessIndex, fingerPrintIndex, 1.0f);
        return Math.round(alphaBlendingValue * 255.0f);
    }

    public float calculateAlphaBlendingValue(int currentBrightnessIndex, float fingerPrintIndex) {
        return calculateAlphaBlendingValue(currentBrightnessIndex, fingerPrintIndex, 1.0f);
    }

    public float calculateAlphaBlendingValue(int currentBrightnessIndex, float fingerPrintIndex, float brCtrl) {
        float gamma = 2.2f;

        if (mGammaArray != null && mGammaArray.length > 0) {
            gamma = mGammaArray[0];
        }

        float currentNits = getNitsForBacklight(currentBrightnessIndex);
        float targetNits = getNitsForBacklight(fingerPrintIndex);

        // Fallback approximation if nits could not be resolved from device overlay
        if (targetNits <= 0.0f) {
            targetNits = fingerPrintIndex > 0 ? fingerPrintIndex : DEFAULT_UDFPS_BRIGHTNESS;
        }
        if (currentNits <= 0.0f) {
            currentNits = currentBrightnessIndex >= 0 ? currentBrightnessIndex : 127.0f;
        }

        Log.i(TAG, "calculateAlphaBlendingValue() currentNits : " + currentNits
                + " , targetNits : " + targetNits
                + " , br_ctrl : " + brCtrl
                + " , gamma : " + gamma);

        if (targetNits <= 0.0f) return 0.0f;

        double base = (currentNits * brCtrl) / targetNits;
        double exponent = 1.0f / gamma;

        return (float) (1.0d - Math.pow(base, exponent));
    }

    public float getNitsForBacklight(float backlight) {
        if (mBrightnessNitsValueStringArray == null || mBrightnessNitsValueStringArray.length == 0) {
            return -1.0f;
        }

        if (mBrightnessBacklightValueStringArray != null && mBrightnessBacklightValueStringArray.length > 0) {
            int bestIdx = 0;
            float minDiff = Float.MAX_VALUE;
            for (int i = 0; i < mBrightnessBacklightValueStringArray.length && i < mBrightnessNitsValueStringArray.length; i++) {
                try {
                    float bl = Float.parseFloat(mBrightnessBacklightValueStringArray[i]);
                    float diff = Math.abs(bl - backlight);
                    if (diff < minDiff) {
                        minDiff = diff;
                        bestIdx = i;
                    }
                } catch (NumberFormatException ignored) {}
            }
            try {
                return Float.parseFloat(mBrightnessNitsValueStringArray[bestIdx]);
            } catch (NumberFormatException ignored) {}
        }

        // Fallback: direct index lookup
        int idx = Math.round(backlight);
        if (idx >= 0 && idx < mBrightnessNitsValueStringArray.length) {
            try {
                return Float.parseFloat(mBrightnessNitsValueStringArray[idx]);
            } catch (NumberFormatException ignored) {}
        } else if (idx >= mBrightnessNitsValueStringArray.length) {
            try {
                return Float.parseFloat(
                        mBrightnessNitsValueStringArray[mBrightnessNitsValueStringArray.length - 1]);
            } catch (NumberFormatException ignored) {}
        }

        return -1.0f;
    }

    public final float getFingerPrintBacklightValue(int requiredNits) {
        if (mBrightnessNitsValueStringArray != null && mBrightnessBacklightValueStringArray != null
                && mBrightnessNitsValueStringArray.length > 1) {
            for (int i = 0; i < mBrightnessNitsValueStringArray.length; i++) {
                try {
                    int thresholdNits = Integer.parseInt(mBrightnessNitsValueStringArray[i]);
                    if (requiredNits <= thresholdNits && i < mBrightnessBacklightValueStringArray.length) {
                        float backlightValue = Float.parseFloat(mBrightnessBacklightValueStringArray[i]);
                        Log.d(TAG, "getFingerPrintBacklightValue() brightnessNits : " + requiredNits
                                + " , FingerPrintBacklightValue : " + backlightValue
                                + " , Size : " + mBrightnessNitsValueStringArray.length);
                        return backlightValue;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return -1.0f;
    }

    /**
     * Dynamically resolves the target UDFPS backlight level by querying optical sensor target
     * nits from the native vendor extension (ISehFingerprint) and mapping via the device overlay.
     */
    public float getUdfpsBrightness() {
        int targetNits = SehFingerprintClient.getTargetNits();
        if (targetNits > 0) {
            float bl = getFingerPrintBacklightValue(targetNits);
            if (bl > 0.0f) {
                Log.i(TAG, "Dynamic UDFPS brightness resolved from vendor extension target ("
                        + targetNits + " nits) via device overlay -> " + bl);
                return bl;
            }
        }

        Log.i(TAG, "Using default fallback UDFPS brightness: " + DEFAULT_UDFPS_BRIGHTNESS);
        return DEFAULT_UDFPS_BRIGHTNESS;
    }
}
