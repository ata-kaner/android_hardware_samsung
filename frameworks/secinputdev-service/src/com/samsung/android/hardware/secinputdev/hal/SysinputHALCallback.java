package com.samsung.android.hardware.secinputdev.hal;

import com.samsung.android.hardware.secinputdev.utils.Common;
import java.util.ArrayList;

public class SysinputHALCallback {
    private static final String TAG = "SysinputHALCallback";
    private static volatile SysinputHALCallback uniqueInstance = null;
    private final SysinputHALInterface sysinputHAL;

    private SysinputHALCallback(SysinputHALInterface hal) {
        this.sysinputHAL = hal;
    }

    public static SysinputHALCallback getInstance(SysinputHALInterface hal) {
        if (uniqueInstance == null) {
            synchronized (SysinputHALCallback.class) {
                if (uniqueInstance == null) {
                    uniqueInstance = new SysinputHALCallback(hal);
                }
            }
        }
        return uniqueInstance;
    }

    public void onReportInformation(int type, String data) {
    }

    public void onReportInformationAidl(int type, String data) {
        if (!Common.REPORT_INFO_HANDEDGE.equals(data) && this.sysinputHAL != null) {
            this.sysinputHAL.onReportInformation(type, data);
        }
    }

    public void onReportRawData(int type, int count, ArrayList<Short> list) {
    }

    public void onReportRawData(int type, int count, int[] list) {
    }
}
