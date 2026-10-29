/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package vendor.samsung.hardware.biometrics.fingerprint;

@VintfStability
oneway interface ISehFingerprintCallback {
    /** Fired when native epoll on scrub_pos detects finger press (0x0F) */
    void onFingerDown(int x, int y);

    /** Fired when finger is lifted (0x10 / 0x11) */
    void onFingerUp();

    /** Fired when the optical sensor begins image exposure/capture */
    void onOpticalCaptureStarted();

    /** Fired when capture completes or fails (immediately turns off illumination) */
    void onOpticalCaptureFinished();
}
