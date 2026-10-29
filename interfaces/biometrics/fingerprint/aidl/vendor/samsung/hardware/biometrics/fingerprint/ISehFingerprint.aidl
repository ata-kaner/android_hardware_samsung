/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package vendor.samsung.hardware.biometrics.fingerprint;

import vendor.samsung.hardware.biometrics.fingerprint.ISehFingerprintCallback;

@VintfStability
interface ISehFingerprint {
    /** Register listener for finger press, lift, and optical capture events */
    void registerCallback(in ISehFingerprintCallback callback);
    void unregisterCallback(in ISehFingerprintCallback callback);

    /** Controls TSP FoD gesture mode (0 = disabled, 1 = screen_on_fast, 2 = aod_strict, 3 = aod_fast) */
    void setFodEnable(int mode);

    /** Computes and applies FoD rectangle based on display metrics and hardware sensor info */
    void updateDisplayInfo(int displayWidth, int displayHeight, float xdpi);

    /** Returns sensor geometry in display pixels: [sensorX, sensorY, sensorRadius, sensorImageSize] */
    int[] getSensorInfo();

    /** Returns currently computed FoD rectangle [left, top, right, bottom] */
    int[] getFodRect();

    /** Sets FoD active touch rectangle in screen coordinates (left, top, right, bottom) */
    void setFodRect(int left, int top, int right, int bottom);

    /** Sets FoD icon visibility flag in the TSP driver */
    void setFodIconVisible(boolean visible);

    /** Sets panel HBM target brightness (/sys/class/lcd/panel/mask_brightness) */
    void setMaskBrightness(int brightness);

    /** Reads sensor target nits (/sys/class/fingerprint/fingerprint/rb) */
    int getSensorTargetNits();

    /** Reads sensor illumination circle color hex string (e.g. "FFFFFF" or "00FF00") from rb */
    String getSensorLightColor();

    /** Informs HAL of display interactive/doze state (0 = OFF, 1 = ON, 2 = DOZE) */
    void setScreenState(int state);
}
