/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "android.hardware.biometrics.fingerprint-service.samsung"

#include "SehFingerprint.h"
#include "LegacyHAL.h"
#include "VendorConstants.h"

#include <fingerprint.sysprop.h>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parsedouble.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <algorithm>

using namespace ::android::fingerprint::samsung;
using ::aidl::android::hardware::biometrics::fingerprint::LegacyHAL;
using ::android::base::ParseDouble;
using ::android::base::ParseInt;
using ::android::base::ReadFileToString;
using ::android::base::Split;
using ::android::base::Tokenize;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;

namespace aidl {
namespace vendor {
namespace samsung {
namespace hardware {
namespace biometrics {
namespace fingerprint {

std::shared_ptr<SehFingerprint> SehFingerprint::sInstance = nullptr;

std::shared_ptr<SehFingerprint> SehFingerprint::getInstance() {
    if (!sInstance) {
        sInstance = ndk::SharedRefBase::make<SehFingerprint>();
    }
    return sInstance;
}

void SehFingerprint::onClientDied(void* cookie) {
    auto* self = static_cast<SehFingerprint*>(cookie);
    if (!self) return;
    LOG(INFO) << "SehFingerprint client died, cleaning callbacks";
    std::lock_guard<std::mutex> lock(self->mCallbackLock);
    self->mCallbacks.erase(
            std::remove_if(self->mCallbacks.begin(), self->mCallbacks.end(),
                           [](const std::shared_ptr<ISehFingerprintCallback>& cb) {
                               return !cb || !AIBinder_isAlive(cb->asBinder().get());
                           }),
            self->mCallbacks.end());
}

SehFingerprint::SehFingerprint()
    : mDeathRecipient(AIBinder_DeathRecipient_new(onClientDied)) {
    if (pipe2(mStopPipe, O_CLOEXEC) < 0) {
        LOG(ERROR) << "Failed to create stop pipe";
    }
}

SehFingerprint::~SehFingerprint() {
    stopPollerThread();
    if (mStopPipe[0] >= 0) close(mStopPipe[0]);
    if (mStopPipe[1] >= 0) close(mStopPipe[1]);
}

::ndk::ScopedAStatus SehFingerprint::registerCallback(
        const std::shared_ptr<ISehFingerprintCallback>& in_callback) {
    if (!in_callback) return ::ndk::ScopedAStatus::ok();

    std::lock_guard<std::mutex> lock(mCallbackLock);
    mCallbacks.push_back(in_callback);
    AIBinder_linkToDeath(in_callback->asBinder().get(), mDeathRecipient.get(), this);
    LOG(INFO) << "Registered SehFingerprintCallback, count: " << mCallbacks.size();
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus SehFingerprint::unregisterCallback(
        const std::shared_ptr<ISehFingerprintCallback>& in_callback) {
    if (!in_callback) return ::ndk::ScopedAStatus::ok();

    std::lock_guard<std::mutex> lock(mCallbackLock);
    AIBinder_unlinkToDeath(in_callback->asBinder().get(), mDeathRecipient.get(), this);
    mCallbacks.erase(
            std::remove_if(mCallbacks.begin(), mCallbacks.end(),
                           [&in_callback](const std::shared_ptr<ISehFingerprintCallback>& cb) {
                               return cb->asBinder().get() == in_callback->asBinder().get();
                           }),
            mCallbacks.end());
    LOG(INFO) << "Unregistered SehFingerprintCallback, remaining: " << mCallbacks.size();
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus SehFingerprint::setFodEnable(int32_t in_mode) {
    mFodMode = in_mode;
    std::string cmd;
    switch (in_mode) {
        case 1:  // SCREEN_ON_FAST
        case 3:  // AOD_FAST
            cmd = "fod_enable,1,1,0";
            startPollerThread();
            break;
        case 2:  // AOD_STRICT
            cmd = "fod_enable,1,0,0";
            startPollerThread();
            break;
        case 0:  // DISABLED
        default:
            cmd = "fod_enable,0";
            stopPollerThread();
            break;
    }
    LOG(INFO) << "setFodEnable(" << in_mode << ") -> " << cmd;
    WriteStringToFile(cmd, "/sys/class/sec/tsp/cmd");
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus SehFingerprint::updateDisplayInfo(int32_t in_displayWidth,
                                                      int32_t in_displayHeight, float in_xdpi) {
    computeFodRect(in_displayWidth, in_displayHeight, in_xdpi);
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus SehFingerprint::getSensorInfo(std::vector<int32_t>* _aidl_return) {
    *_aidl_return = {mSensorInfo.x, mSensorInfo.y, mSensorInfo.radius, mSensorInfo.imageSize};
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus SehFingerprint::getFodRect(std::vector<int32_t>* _aidl_return) {
    *_aidl_return = {mFodRect.left, mFodRect.top, mFodRect.right, mFodRect.bottom};
    return ::ndk::ScopedAStatus::ok();
}

void SehFingerprint::computeFodRect(int32_t displayWidth, int32_t displayHeight, float xdpi) {
    double mmToPx = (xdpi > 0.0f) ? (xdpi / 25.4) : (420.0 / 25.4);

    double marginBottom = 0.0;
    double marginLeft = 0.0;
    double areaWidth = 0.0;
    double areaHeight = 0.0;
    double activeArea = 0.0;
    double imageSize = 0.0;
    bool parsedSysfs = false;

    std::string raw;
    if (ReadFileToString("/sys/class/fingerprint/fingerprint/position", &raw)) {
        std::vector<std::string> parts = Split(Trim(raw), ",");
        if (parts.size() >= 4 &&
            ParseDouble(parts[0], &marginBottom) &&
            ParseDouble(parts[1], &marginLeft) &&
            ParseDouble(parts[2], &areaWidth) &&
            ParseDouble(parts[3], &areaHeight)) {
            activeArea = areaWidth;
            if (parts.size() >= 6) {
                ParseDouble(parts[5], &activeArea);
            }
            imageSize = areaWidth;
            if (parts.size() >= 8) {
                double parsedImageSize = 0.0;
                if (ParseDouble(parts[7], &parsedImageSize) && parsedImageSize > 0.0) {
                    imageSize = parsedImageSize;
                }
            }
            parsedSysfs = true;
            LOG(INFO) << "Parsed sensor position from sysfs: " << raw
                      << " (marginB=" << marginBottom << ", marginL=" << marginLeft
                      << ", areaW=" << areaWidth << ", areaH=" << areaHeight
                      << ", activeArea=" << activeArea << ", imageSize=" << imageSize << ")";
        }
    }

    int32_t sensorX = 0;
    int32_t sensorY = 0;
    int32_t sensorRadius = 0;
    int32_t sensorImageSize = 0;

    if (parsedSysfs) {
        sensorX = static_cast<int32_t>((displayWidth / 2.0) + (marginLeft * mmToPx));
        sensorY = static_cast<int32_t>(displayHeight - (marginBottom * mmToPx) - ((areaHeight / 2.0) * mmToPx));
        sensorImageSize = static_cast<int32_t>(imageSize * mmToPx);
        sensorRadius = sensorImageSize / 2;

        int32_t fodSize = static_cast<int32_t>(activeArea * mmToPx);
        int32_t fodLeft = (displayWidth / 2) - (fodSize / 2) + static_cast<int32_t>(marginLeft * mmToPx);
        int32_t fodTop = static_cast<int32_t>(displayHeight - (marginBottom * mmToPx) - ((areaHeight / 2.0) * mmToPx) - (fodSize / 2.0));
        mFodRect = {fodLeft, fodTop, fodLeft + fodSize, fodTop + fodSize};
    } else {
        // Fallback 1: ro.vendor.fingerprint.sensor_location property
        std::string loc = FingerprintHalProperties::sensor_location().value_or("");
        if (!loc.empty()) {
            std::vector<std::string> sensors = Split(loc, ";");
            if (!sensors.empty()) {
                std::vector<std::string> dim = Split(sensors[0], "|");
                if (dim.size() >= 3) {
                    ParseInt(dim[0], &sensorX);
                    ParseInt(dim[1], &sensorY);
                    ParseInt(dim[2], &sensorRadius);
                    sensorImageSize = sensorRadius * 2;
                }
            }
        }

        if (sensorRadius > 0) {
            mFodRect = {sensorX - sensorRadius, sensorY - sensorRadius,
                        sensorX + sensorRadius, sensorY + sensorRadius};
            LOG(INFO) << "Fallback FoD rect from sensor_location prop: [" << mFodRect.left
                      << ", " << mFodRect.top << ", " << mFodRect.right << ", " << mFodRect.bottom << "]";
        } else {
            // Fallback 2: Generic Samsung default geometry
            marginBottom = 13.77;
            marginLeft = 0.0;
            areaHeight = 4.0;
            activeArea = 14.80;
            imageSize = 12.0;

            sensorX = static_cast<int32_t>((displayWidth / 2.0) + (marginLeft * mmToPx));
            sensorY = static_cast<int32_t>(displayHeight - (marginBottom * mmToPx) - ((areaHeight / 2.0) * mmToPx));
            sensorImageSize = static_cast<int32_t>(imageSize * mmToPx);
            sensorRadius = sensorImageSize / 2;

            int32_t fodSize = static_cast<int32_t>(activeArea * mmToPx);
            int32_t fodLeft = (displayWidth / 2) - (fodSize / 2) + static_cast<int32_t>(marginLeft * mmToPx);
            int32_t fodTop = static_cast<int32_t>(displayHeight - (marginBottom * mmToPx) - ((areaHeight / 2.0) * mmToPx) - (fodSize / 2.0));
            mFodRect = {fodLeft, fodTop, fodLeft + fodSize, fodTop + fodSize};
            LOG(WARNING) << "Fallback FoD rect using built-in defaults: [" << mFodRect.left
                         << ", " << mFodRect.top << ", " << mFodRect.right << ", " << mFodRect.bottom << "]";
        }
    }

    mSensorInfo = {sensorX, sensorY, sensorRadius, sensorImageSize};
    LOG(INFO) << "SensorInfo: center=(" << sensorX << ", " << sensorY << ") radius="
              << sensorRadius << " imageSize=" << sensorImageSize
              << " FoD rect: [" << mFodRect.left << ", " << mFodRect.top << ", "
              << mFodRect.right << ", " << mFodRect.bottom << "] screen="
              << displayWidth << "x" << displayHeight << " xdpi=" << xdpi;

    setFodRect(mFodRect.left, mFodRect.top, mFodRect.right, mFodRect.bottom);
}

::ndk::ScopedAStatus SehFingerprint::setFodRect(int32_t in_left, int32_t in_top,
                                               int32_t in_right, int32_t in_bottom) {
    mFodRect = {in_left, in_top, in_right, in_bottom};
    std::string cmd = "set_fod_rect," + std::to_string(in_left) + "," +
                      std::to_string(in_top) + "," + std::to_string(in_right) + "," +
                      std::to_string(in_bottom);
    LOG(INFO) << "setFodRect: " << cmd;
    WriteStringToFile(cmd, "/sys/class/sec/tsp/cmd");
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus SehFingerprint::setFodIconVisible(bool in_visible) {
    std::string cmd = std::string("fod_icon_visible,") + (in_visible ? "1" : "0");
    WriteStringToFile(cmd, "/sys/class/sec/tsp/cmd");
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus SehFingerprint::setMaskBrightness(int32_t in_brightness) {
    WriteStringToFile(std::to_string(in_brightness),
                      "/sys/class/lcd/panel/mask_brightness");
    return ::ndk::ScopedAStatus::ok();
}

std::string SehFingerprint::getOpticalBrightnessConfig() {
    std::string content;
    if (ReadFileToString("/sys/class/fingerprint/fingerprint/rb", &content)) {
        std::string trimmed = Trim(content);
        if (!trimmed.empty()) {
            LOG(INFO) << "Optical brightness config from sysfs: " << trimmed;
            return trimmed;
        }
    }

    return "";
}

::ndk::ScopedAStatus SehFingerprint::getSensorTargetNits(int32_t* _aidl_return) {
    std::string config = getOpticalBrightnessConfig();
    if (!config.empty()) {
        std::vector<std::string> parts = Split(config, ",");
        if (!parts.empty()) {
            int val = 0;
            if (ParseInt(Trim(parts[0]), &val) && val > 0) {
                LOG(INFO) << "getSensorTargetNits: " << val;
                *_aidl_return = val;
                return ::ndk::ScopedAStatus::ok();
            }
        }
    }
    *_aidl_return = -1;
    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus SehFingerprint::getSensorLightColor(std::string* _aidl_return) {
    std::string config = getOpticalBrightnessConfig();
    if (!config.empty()) {
        std::vector<std::string> parts = Split(config, ",");
        // Format: "targetNits,backlightLevel,calibrationVal,colorHex[,lowBrightnessColorHex]"
        if (parts.size() > 3) {
            std::string color = Trim(parts[3]);
            if (!color.empty()) {
                LOG(INFO) << "getSensorLightColor: " << color;
                *_aidl_return = color;
                return ::ndk::ScopedAStatus::ok();
            }
        }
    }
    // Default fallback to white
    *_aidl_return = "FFFFFF";
    return ::ndk::ScopedAStatus::ok();
}

void SehFingerprint::notifyOpticalCaptureStarted() {
    std::lock_guard<std::mutex> lock(mCallbackLock);
    for (auto& cb : mCallbacks) {
        if (cb) cb->onOpticalCaptureStarted();
    }
}

void SehFingerprint::notifyOpticalCaptureFinished() {
    std::lock_guard<std::mutex> lock(mCallbackLock);
    for (auto& cb : mCallbacks) {
        if (cb) cb->onOpticalCaptureFinished();
    }
}

void SehFingerprint::dispatchFingerDown(int32_t x, int32_t y) {
    std::lock_guard<std::mutex> lock(mCallbackLock);
    for (auto& cb : mCallbacks) {
        if (cb) cb->onFingerDown(x, y);
    }
}

void SehFingerprint::dispatchFingerUp() {
    std::lock_guard<std::mutex> lock(mCallbackLock);
    for (auto& cb : mCallbacks) {
        if (cb) cb->onFingerUp();
    }
}

void SehFingerprint::startPollerThread() {
    if (mPollerRunning.exchange(true)) {
        return;  // already running
    }

    mPollerThread = std::thread(&SehFingerprint::pollerLoop, this);
    LOG(INFO) << "Started native scrub_pos poller thread";
}

void SehFingerprint::stopPollerThread() {
    if (!mPollerRunning.exchange(false)) {
        return;  // not running
    }

    // Wake up poll with stop pipe
    char wake = 'q';
    (void)write(mStopPipe[1], &wake, 1);

    if (mPollerThread.joinable()) {
        mPollerThread.join();
    }

    // Drain stop pipe
    char drain[16];
    while (read(mStopPipe[0], drain, sizeof(drain)) > 0) {}

    LOG(INFO) << "Stopped native scrub_pos poller thread";
}

void SehFingerprint::pollerLoop() {
    int fd = open("/sys/devices/virtual/sec/tsp/scrub_pos", O_RDONLY);
    if (fd < 0) {
        fd = open("/sys/class/sec/tsp/scrub_pos", O_RDONLY);
    }
    if (fd < 0) {
        LOG(WARNING) << "Could not open scrub_pos for polling";
        mPollerRunning = false;
        return;
    }

    struct pollfd fds[2];
    fds[0].fd = fd;
    fds[0].events = POLLPRI | POLLERR;
    fds[1].fd = mStopPipe[0];
    fds[1].events = POLLIN;

    char buf[128];
    lseek(fd, 0, SEEK_SET);
    (void)read(fd, buf, sizeof(buf) - 1);

    while (mPollerRunning) {
        int ret = poll(fds, 2, -1);
        if (ret < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (fds[1].revents & POLLIN) {
            break;  // Stop signal
        }

        if (fds[0].revents & (POLLPRI | POLLERR)) {
            lseek(fd, 0, SEEK_SET);
            ssize_t n = read(fd, buf, sizeof(buf) - 1);
            if (n > 0) {
                buf[n] = '\0';
                std::vector<std::string> tokens = Tokenize(buf, " \t\r\n");
                int event = -1;
                for (const auto& token : tokens) {
                    if (token == "0x0F" || token == "0x0f" || token == "15") {
                        event = 0x0F;
                    } else if (token == "0x10" || token == "0x11" || token == "16" ||
                               token == "17") {
                        event = 0x10;
                    }
                }

                int x = -1;
                int y = -1;
                std::vector<int> numbers;
                for (const auto& token : tokens) {
                    int val = 0;
                    if (ParseInt(token, &val) && val > 0 && val != 15 &&
                        val != 16 && val != 17) {
                        numbers.push_back(val);
                    }
                }
                if (numbers.size() >= 2) {
                    x = numbers[0];
                    y = numbers[1];
                }

                if (event == 0x0F) {
                    LOG(INFO) << "Native scrub_pos: Finger Down (" << x << ", " << y << ")";
                    dispatchFingerDown(x, y);
                } else if (event == 0x10) {
                    LOG(INFO) << "Native scrub_pos: Finger Up";
                    dispatchFingerUp();
                }
            }
        }
    }

    close(fd);
}

}  // namespace fingerprint
}  // namespace biometrics
}  // namespace hardware
}  // namespace samsung
}  // namespace vendor
}  // namespace aidl
