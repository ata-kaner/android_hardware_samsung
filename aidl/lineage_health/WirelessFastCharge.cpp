/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include "WirelessFastCharge.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>

#define LOG_TAG "vendor.lineage.health-service.samsung"

namespace aidl {
namespace vendor {
namespace lineage {
namespace health {

WirelessFastCharge::WirelessFastCharge() : mSupportedModes(static_cast<int64_t>(WirelessFastChargeMode::NONE)) {
    if (!access(WAFC_DISABLE_NODE, F_OK))
        mSupportedModes |= static_cast<int64_t>(WirelessFastChargeMode::WIRELESS_FAST_CHARGE);
}

ndk::ScopedAStatus WirelessFastCharge::getSupportedWirelessFastChargeModes(int64_t* _aidl_return) {
    *_aidl_return = mSupportedModes;

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus WirelessFastCharge::getWirelessFastChargeMode(WirelessFastChargeMode* _aidl_return) {
    std::string wafcDisableContent;

    if (!android::base::ReadFileToString(WAFC_DISABLE_NODE, &wafcDisableContent, true)) {
        LOG(ERROR) << "Failed to open " << WAFC_DISABLE_NODE << ", " << strerror(errno);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    wafcDisableContent = android::base::Trim(wafcDisableContent);

    if (wafcDisableContent == "1") {
        *_aidl_return = WirelessFastChargeMode::NONE;
        return ndk::ScopedAStatus::ok();
    } else if (wafcDisableContent == "2") {
        *_aidl_return = WirelessFastChargeMode::WIRELESS_FAST_CHARGE;
    } else {
        LOG(ERROR) << "Invalid wafc_disable value read: " << wafcDisableContent;
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    return ndk::ScopedAStatus::ok();
}

#define WRITE_WITH_CHECK(file, value, is_error)                                               \
    if (!android::base::WriteStringToFile(value, file, true)) {                               \
        LOG(is_error ? ERROR : VERBOSE)                                                       \
                << "Failed to write " << value << " to " << file << ": " << strerror(errno);  \
        if (is_error) return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION); \
    }

ndk::ScopedAStatus WirelessFastCharge::setWirelessFastChargeMode(WirelessFastChargeMode in_mode,
                                                 WirelessFastChargeMode* _aidl_return) {
    if (!(static_cast<int64_t>(in_mode) & mSupportedModes)) {
        LOG(ERROR) << "Mode " << toString(in_mode) << " not supported!";
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    switch (in_mode) {
        case WirelessFastChargeMode::NONE:
            WRITE_WITH_CHECK(WAFC_DISABLE_NODE, "1", true)
            break;
        case WirelessFastChargeMode::WIRELESS_FAST_CHARGE:
            WRITE_WITH_CHECK(WAFC_DISABLE_NODE, "2", true)
            break;
    }

    return getWirelessFastChargeMode(_aidl_return);
}

binder_status_t WirelessFastCharge::dump(int fd, const char** /*args*/, uint32_t /*numArgs*/) {
    int64_t supportedWirelessFastChargeModes;
    getSupportedWirelessFastChargeModes(&supportedWirelessFastChargeModes);

    WirelessFastChargeMode WirelessfastChargeMode;
    getWirelessFastChargeMode(&WirelessfastChargeMode);

    dprintf(fd, "Wireless fast charge supported modes: %ld\n", static_cast<long>(supportedWirelessFastChargeModes));
    dprintf(fd, "Wireless fast charge mode: %d\n", static_cast<int>(WirelessfastChargeMode));

    return STATUS_OK;
}

}  // namespace health
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
