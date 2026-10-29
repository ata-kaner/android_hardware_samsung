/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/samsung/hardware/biometrics/fingerprint/BnSehFingerprint.h>
#include <aidl/vendor/samsung/hardware/biometrics/fingerprint/ISehFingerprintCallback.h>

#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace aidl {
namespace vendor {
namespace samsung {
namespace hardware {
namespace biometrics {
namespace fingerprint {

class SehFingerprint : public BnSehFingerprint {
  public:
    SehFingerprint();
    virtual ~SehFingerprint();

    static std::shared_ptr<SehFingerprint> getInstance();

    // AIDL methods
    ::ndk::ScopedAStatus registerCallback(
            const std::shared_ptr<ISehFingerprintCallback>& in_callback) override;
    ::ndk::ScopedAStatus unregisterCallback(
            const std::shared_ptr<ISehFingerprintCallback>& in_callback) override;
    ::ndk::ScopedAStatus setFodEnable(int32_t in_mode) override;
    ::ndk::ScopedAStatus updateDisplayInfo(int32_t in_displayWidth, int32_t in_displayHeight,
                                          float in_xdpi) override;
    ::ndk::ScopedAStatus getSensorInfo(std::vector<int32_t>* _aidl_return) override;
    ::ndk::ScopedAStatus getFodRect(std::vector<int32_t>* _aidl_return) override;
    ::ndk::ScopedAStatus setFodRect(int32_t in_left, int32_t in_top, int32_t in_right,
                                   int32_t in_bottom) override;
    ::ndk::ScopedAStatus setFodIconVisible(bool in_visible) override;
    ::ndk::ScopedAStatus setMaskBrightness(int32_t in_brightness) override;
    ::ndk::ScopedAStatus getSensorTargetNits(int32_t* _aidl_return) override;
    ::ndk::ScopedAStatus getSensorLightColor(std::string* _aidl_return) override;
    ::ndk::ScopedAStatus setScreenState(int32_t in_state) override;

    // Internal triggers from Fingerprint Session / Notify
    void notifyOpticalCaptureStarted();
    void notifyOpticalCaptureFinished();

  private:
    std::string getOpticalBrightnessConfig();
    void startPollerThread();
    void stopPollerThread();
    void pollerLoop();
    void dispatchFingerDown(int32_t x, int32_t y);
    void dispatchFingerUp();

    std::mutex mCallbackLock;
    std::vector<std::shared_ptr<ISehFingerprintCallback>> mCallbacks;
    ::ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;

    static void onClientDied(void* cookie);

    std::atomic<bool> mPollerRunning{false};
    std::thread mPollerThread;
    int mStopPipe[2]{-1, -1};

    int32_t mScreenState{1};
    int32_t mFodMode{0};

    struct SensorInfo {
        int32_t x{0};
        int32_t y{0};
        int32_t radius{0};
        int32_t imageSize{0};
    };
    SensorInfo mSensorInfo;

    struct FodRect {
        int32_t left{0};
        int32_t top{0};
        int32_t right{0};
        int32_t bottom{0};
    };
    FodRect mFodRect;
    void computeFodRect(int32_t displayWidth, int32_t displayHeight, float xdpi);

    static std::shared_ptr<SehFingerprint> sInstance;
};

}  // namespace fingerprint
}  // namespace biometrics
}  // namespace hardware
}  // namespace samsung
}  // namespace vendor
}  // namespace aidl
