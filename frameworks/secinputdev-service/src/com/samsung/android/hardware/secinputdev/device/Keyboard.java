package com.samsung.android.hardware.secinputdev.device;

public class Keyboard extends SemInputDevice {
    public Keyboard(String name, int devid, int feature, String cmdlist) {
        super(name, devid, feature, cmdlist);
    }

    @Override
    protected boolean supportActivate() {
        return true;
    }
}
