package com.samsung.android.hardware.secinputdev.device;

import com.samsung.android.hardware.secinputdev.utils.Utilities;
import java.util.HashMap;
import java.util.Map;

public class SemInputDeviceFactory {
    private static final Map<Integer, SemInputDevice> devices = new HashMap();

    public static synchronized SemInputDevice create(String name, int devid, int feature, String cmdlist) {
        SemInputDevice device;
        SemInputDevice device2 = devices.get(Integer.valueOf(devid));
        if (device2 != null) {
            return device2;
        }
        if (Utilities.isDevidTsp(devid)) {
            device = new Touch(name, devid, feature, cmdlist);
        } else if (Utilities.isDevidSpen(devid)) {
            device = new Spen(name, devid, feature, cmdlist);
        } else if (Utilities.isDevidKeyboard(devid)) {
            device = new Keyboard(name, devid, feature, cmdlist);
        } else {
            device = new NotDefined();
        }
        devices.put(Integer.valueOf(devid), device);
        return device;
    }

    public static synchronized SemInputDevice get(int devid) {
        return devices.get(Integer.valueOf(devid));
    }

    public static synchronized Touch getTouch(int devid) {
        if (!Utilities.isDevidTsp(devid)) {
            return null;
        }
        return (Touch) devices.get(Integer.valueOf(devid));
    }
}
