/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <thread>
#include <chrono>
#include <sys/inotify.h>
#include <poll.h>
#include <unistd.h>
#include <fcntl.h>
#include <fstream>
#include <string>
#include <sstream>

#include "SecUdfpsHelper.h"

namespace aidl {
namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {

SecUdfpsHelper::SecUdfpsHelper(std::shared_ptr<ISehSysInputDev> sehInput)
    : mSehInput(std::move(sehInput)) {
#if QCOM_UDFPS
    mHbmWatcher.start();
#endif
}

template <typename T>
void SecUdfpsHelper::set(const std::string& path, const T& value) {
    std::ofstream file(path);
    if (!file.is_open()) {
        LOG(ERROR) << "[SecUdfpsHelper] Failed to open file at path: " << path;
        return;
    }

    file << value << std::endl;
    if (file.fail()) {
        LOG(ERROR) << "[SecUdfpsHelper] Failed to write value: " << value << " to path: " << path;
    }
}

template <typename T>
T SecUdfpsHelper::get(const std::string& path, const T& defaultValue) {
    std::ifstream file(path);
    T result = defaultValue;

    if (file.is_open()) {
        file >> result;
        file.close();
    } else {
        LOG(ERROR) << "[SecUdfpsHelper] Failed to open file at path: " << path;
    }

    return result;
}

/* TEMP DISABLE
void SecUdfpsHelper::runCommand(int inputDevice, int inputCommand, const std::string& cmdString) {
    if (mSehInput) {
        mSehInput->runCommand(inputDevice, inputCommand, cmdString);
    } else {
        LOG(ERROR) << "[SecUdfpsHelper] SysInput HAL isn't registered to Fingerprint HAL";
    }
}
*/

int SecUdfpsHelper::setProperty(int inputDevice, int inputCommand, const std::string& cmdString) {
    if (!mSehInput) {
        LOG(ERROR) << "[SecUdfpsHelper] SysInput HAL isn't registered to Fingerprint HAL";
        return -1;
    }

    int32_t result = -1;
    auto status = mSehInput->setProperty(inputDevice, inputCommand, cmdString, &result);
    if (!status.isOk()) {
        LOG(ERROR) << "[SecUdfpsHelper] SysInput::setProperty failed: " << status.getMessage();
        return -1;
    }

    return result;
}

std::string SecUdfpsHelper::getProperty(int inputDevice, int inputCommand) {
    if (!mSehInput) {
        LOG(ERROR) << "[SecUdfpsHelper] SysInput HAL isn't registered to Fingerprint HAL";
        return "";
    }

    std::string result;
    auto status = mSehInput->getProperty(inputDevice, inputCommand, &result);
    if (!status.isOk()) {
        LOG(ERROR) << "[SecUdfpsHelper] SysInput::getProperty failed: " << status.getMessage();
        return "";
    }

    return result;
}

int SecUdfpsHelper::setFodEnable(int i, int i2, int i3) {
    std::ostringstream oss;
    if (i == 1) {
        oss << i << "," << i2 << "," << i3;
    } else {
        oss << i;
    }
    return setProperty(static_cast<int>(SemInputDevice::DEFAULT_TSP), static_cast<int>(SemInputCommand::FOD), oss.str());
}

int SecUdfpsHelper::setFodIconVisible(int i) {
    return setProperty(static_cast<int>(SemInputDevice::DEFAULT_TSP), static_cast<int>(SemInputCommand::FOD_ICON_VISIBLE), std::to_string(i) + "");
}

int SecUdfpsHelper::setFodLpMode(int i) {
    return setProperty(static_cast<int>(SemInputDevice::DEFAULT_TSP), static_cast<int>(SemInputCommand::FOD_LP), std::to_string(i) + "");
}

// Currently, calculating FodRect is complicated. Request original coordinates from stock OneUI temporarily as a FP HAL property.
int SecUdfpsHelper::setFodRect(int i, int i2, int i3, int i4) {
    std::ostringstream oss;
    oss << i << "," << i2 << "," << i3 << "," << i4;
    return setProperty(static_cast<int>(SemInputDevice::DEFAULT_TSP), static_cast<int>(SemInputCommand::FOD_RECT), oss.str());
}

#if QCOM_UDFPS
void SecUdfpsHelper::HbmWatcher::start() {
    std::thread([this]() {
        monitorLoop();
    }).detach();
}

void SecUdfpsHelper::HbmWatcher::monitorLoop() {
    const std::string enabledPath = SecUdfpsHelper::FP_MASK_ENABLED;
    const std::string brightReadPath = SecUdfpsHelper::FP_MASK_DEFAULT_BRIGHT;
    const std::string brightWritePath = SecUdfpsHelper::HBM_BRIGHT_WO;

    std::string lastValue;

    int fd = inotify_init1(IN_NONBLOCK);
    int wd = inotify_add_watch(fd, enabledPath.c_str(), IN_MODIFY);

    if (fd < 0 || wd < 0) {
        LOG(WARNING) << "[SecUdfpsHelper] inotify not supported on " << enabledPath << ", falling back to polling.";
        close(fd);

        while (true) {
            std::ifstream in(enabledPath);
            std::string value;
            in >> value;

            if (value == "1" && lastValue != "1") {
                std::ifstream brightIn(brightReadPath);
                std::string brightness;
                brightIn >> brightness;

                std::ofstream brightOut(brightWritePath);
                brightOut << brightness << std::endl;

                LOG(INFO) << "[SecUdfpsHelper] Set HBM brightness to: " << brightness;
            }

            lastValue = value;
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }

        return;
    }

    LOG(INFO) << "[SecUdfpsHelper] Started monitoring " << enabledPath;

    char buffer[4096];
    struct pollfd pfd = { .fd = fd, .events = POLLIN };

    while (true) {
        int res = poll(&pfd, 1, -1);
        if (res > 0 && (pfd.revents & POLLIN)) {
            read(fd, buffer, sizeof(buffer));

            std::ifstream in(enabledPath);
            std::string value;
            in >> value;

            if (value == "1" && lastValue != "1") {
                std::ifstream brightIn(brightReadPath);
                std::string brightness;
                brightIn >> brightness;

                std::ofstream brightOut(brightWritePath);
                brightOut << brightness << std::endl;

                LOG(INFO) << "[SecUdfpsHelper] Set HBM brightness to: " << brightness;
            }

            lastValue = value;
        }
    }

    inotify_rm_watch(fd, wd);
    close(fd);
}
#endif

} // namespace fingerprint
} // namespace biometrics
} // namespace hardware
} // namespace android
} // namespace aidl
