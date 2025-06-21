/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/samsung/hardware/sysinput/ISehSysInputDev.h>
#include <android/binder_manager.h>
#include <android/binder_interface_utils.h>
#include <android-base/logging.h>
#include <memory>

#include "SemInputConstants.h.h"

inline std::shared_ptr<aidl::vendor::samsung::hardware::sysinput::ISehSysInputDev>
getSehSysInputDev(const std::string& instanceName = "default") {
    using aidl::vendor::samsung::hardware::sysinput::ISehSysInputDev;

#if USE_SYSINPUT_HAL
    const std::string fullInstance = std::string() + ISehSysInputDev::descriptor + "/" + instanceName;

    ndk::SpAIBinder binder(AServiceManager_waitForService(fullInstance.c_str()));
    if (!binder.get()) {
        LOG(ERROR) << "Failed to get ISehSysInputDev service: " << fullInstance;
        return nullptr;
    }

    std::shared_ptr<ISehSysInputDev> dev = ISehSysInputDev::fromBinder(binder);
    if (!dev) {
        LOG(ERROR) << "Failed to convert binder to ISehSysInputDev interface";
    } 

    return dev;
#else
    LOG(ERROR) << "Fingerprint HAL doesn't use SysInput Interface";
    return nullptr;
#endif
}

namespace aidl {
namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {

using ::aidl::vendor::samsung::hardware::sysinput::ISehSysInputDev;

class SecUdfpsHelper {
public:
    explicit SecUdfpsHelper(std::shared_ptr<ISehSysInputDev> sehInput);

    int setFodEnable(int i, int i2, int i3);
    int setFodIconVisible(int i);
    int setFodLpMode(int i);
    int setFodRect(int i, int i2, int i3, int i4);

    template <typename T> T get(const std::string& path, const T& def);
    template <typename T> void set(const std::string& path, const T& value);

private:
    std::shared_ptr<ISehSysInputDev> mSehInput;

    // void runCommand(int inputDevice, int inputCommand, const std::string& cmdString);
    int setProperty(int inputDevice, int inputCommand, const std::string& cmdString);
    std::string getProperty(int inputDevice, int inputCommand);

#if QCOM_UDFPS
    class HbmWatcher {
        public:
            void start();
        private:
            void monitorLoop();
    };
    HbmWatcher mHbmWatcher;

/*
 * hbm_enable -> Provides info about HBM status
 * actual_hbm_brightness -> Provides default brightness value used by HBM to FP HAL
 * actual_mask_brightness -> Current brightness value of the HBM
 * mask_brightness -> Sets brightness of the HBM
*/
    static constexpr const char* FP_MASK_ENABLED = "/sys/devices/virtual/lcd/panel/finger_mask_state";
    static constexpr const char* FP_MASK_DEFAULT_BRIGHT = "/sys/devices/virtual/lcd/panel/finger_mask_brightness";
    static constexpr const char* HBM_BRIGHT_RO = "/sys/devices/virtual/lcd/panel/actual_mask_brightness";
    static constexpr const char* HBM_BRIGHT_WO = "/sys/devices/virtual/lcd/panel/mask_brightness";
    static constexpr int MASK_BRIGHTNESS = 319;
#endif

};

} // namespace fingerprint
} // namespace biometrics
} // namespace hardware
} // namespace android
} // namespace aidl
