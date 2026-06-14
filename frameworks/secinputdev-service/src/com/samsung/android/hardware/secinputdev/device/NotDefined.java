package com.samsung.android.hardware.secinputdev.device;

public class NotDefined extends SemInputDevice {
    public NotDefined() {
        super("NotDefined", 0, 0, "NG");
    }

    @Override
    public String toString() {
        return this.name + "(" + this.devid + ")";
    }
}
