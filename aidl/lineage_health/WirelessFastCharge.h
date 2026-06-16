/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lineage/health/BnWirelessFastCharge.h>

#define WAFC_DISABLE_NODE "/sys/class/power_supply/battery/batt_hv_wireless_pad_ctrl"

namespace aidl {
namespace vendor {
namespace lineage {
namespace health {

class WirelessFastCharge : public BnWirelessFastCharge {
  public:
    WirelessFastCharge();

    ndk::ScopedAStatus getSupportedWirelessFastChargeModes(int64_t* _aidl_return) override;
    ndk::ScopedAStatus getWirelessFastChargeMode(WirelessFastChargeMode* _aidl_return) override;
    ndk::ScopedAStatus setWirelessFastChargeMode(WirelessFastChargeMode in_mode,
                                         WirelessFastChargeMode* _aidl_return) override;

    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;

  private:
    int64_t mSupportedModes;
};

}  // namespace health
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
