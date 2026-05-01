package com.samsung.android.wifi;

import android.annotation.Nullable;

public class SemWifiManager {

    @Nullable
    public String getFactoryMacAddress() {
        return null;
    }

    // Added so reflective calls succeed if SemWifiManager instance exists.
    public void setImsCallEstablished(boolean established) {
        // no-op on AOSP/Lineage
    }

    // Added so reflective calls succeed if SemWifiManager instance exists.
    public void setMaxDtimInSuspendMode(boolean enabled) {
        // no-op on AOSP/Lineage
    }
}
