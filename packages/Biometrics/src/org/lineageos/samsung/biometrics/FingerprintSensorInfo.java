/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.graphics.Point;
import android.util.Log;
import android.view.Surface;

/**
 * Sensor geometry (optical path) in display coordinates, accounting for screen rotation.
 * Sourced directly from hardware sysfs (/sys/class/fingerprint/fingerprint/position) via
 * the native SehFingerprint HAL.
 */
public class FingerprintSensorInfo {
    private static final String TAG = "SB_SensorInfo";

    public final int sensorX;        // centre X, natural (portrait) orientation
    public final int sensorY;        // centre Y, natural (portrait) orientation
    public final int sensorRadius;
    public final int sensorImageSize;
    public final String lightColor;

    public FingerprintSensorInfo(int x, int y, int radius, int imageSize) {
        this(x, y, radius, imageSize, null);
    }

    public FingerprintSensorInfo(int x, int y, int radius, int imageSize, String lightColor) {
        sensorX = x;
        sensorY = y;
        sensorRadius = radius;
        sensorImageSize = imageSize > 0 ? imageSize : radius * 2;
        this.lightColor = (lightColor != null && !lightColor.trim().isEmpty())
                ? lightColor.trim() : "FFFFFF";
        Log.i(TAG, "sensor centre=(" + x + "," + y + ") radius=" + radius
                + " imageSize=" + sensorImageSize + " lightColor=" + this.lightColor);
    }

    /**
     * Fallback geometry using typical Samsung physical sensor metrics if HAL returns empty.
     */
    public static FingerprintSensorInfo createDefault(int displayWidth, int displayHeight, float xdpi) {
        return createDefault(displayWidth, displayHeight, xdpi, null);
    }

    public static FingerprintSensorInfo createDefault(int displayWidth, int displayHeight, float xdpi,
                                                      String lightColor) {
        double mmToPx = (xdpi > 0.0f) ? (xdpi / 25.4) : (420.0 / 25.4);
        int sensorX = displayWidth / 2;
        int sensorY = (int) (displayHeight - (14.31 * mmToPx) - ((9.10 / 2.0) * mmToPx));
        int imageSize = (int) (12.00 * mmToPx);
        int radius = imageSize / 2;
        Log.w(TAG, "Using default sensor geometry: centre=(" + sensorX + "," + sensorY + ") radius=" + radius);
        return new FingerprintSensorInfo(sensorX, sensorY, radius, imageSize, lightColor);
    }

    /**
     * Top-left corner of the sensor image rect for the given display size and rotation.
     */
    public Point getSensorImagePoint(Point displaySize, int rotation) {
        int half = sensorImageSize / 2;
        int x;
        int y;

        switch (rotation) {
            case Surface.ROTATION_90:
                x = sensorY - half;
                y = displaySize.y - sensorX - half;
                break;
            case Surface.ROTATION_180:
                x = displaySize.x - sensorX - half;
                y = displaySize.y - sensorY - half;
                break;
            case Surface.ROTATION_270:
                x = displaySize.x - sensorY - half;
                y = sensorX - half;
                break;
            case Surface.ROTATION_0:
            default:
                x = sensorX - half;
                y = sensorY - half;
                break;
        }
        return new Point(x, y);
    }
}
