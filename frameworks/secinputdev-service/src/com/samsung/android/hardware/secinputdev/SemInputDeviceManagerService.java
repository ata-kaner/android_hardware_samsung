package com.samsung.android.hardware.secinputdev;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import com.samsung.android.hardware.secinputdev.device.SemInputDevice;
import com.samsung.android.hardware.secinputdev.hal.SysinputHALFactory;
import com.samsung.android.hardware.secinputdev.hal.SysinputHALInterface;
import com.samsung.android.hardware.secinputdev.utils.SemInputConstants;
import com.samsung.android.hardware.secinputdev.utils.Utilities;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class SemInputDeviceManagerService extends ISemInputDeviceManager.Stub {
    private static final String TAG = "SemInputDeviceManagerService";
    private static Handler mainHandler = null;
    private final SemInputCommandService commandService;
    private final Context context;
    private final SysinputHALInterface sysinputHAL;
    private final HandlerThread mainHandlerThread = new HandlerThread("SemInputMainHandlerThread");

    public interface MainHandlerMessage {
        int HAL_FACTORY_REGISTER_CALLBACK = 1;
        int SYSTEM_READY = 2;
    }

    public SemInputDeviceManagerService(Context context) {
        this.context = context;
        this.mainHandlerThread.start();
        mainHandler = new MainHandler(this.mainHandlerThread.getLooper());
        this.sysinputHAL = SysinputHALFactory.connectHAL();
        registerCallbackWithRetries(5);
        this.commandService = new SemInputCommandService(this.sysinputHAL);
        Log.d(TAG, "done");
    }

    public int registerCallbackWithRetries(int retry) {
        for (int ii = 1; ii <= retry; ii++) {
            if (SysinputHALFactory.registerCallback() >= 0) {
                return 0;
            }
            try {
                Thread.sleep(20L);
            } catch (Exception e) {
                Log.e(TAG, "registerCallbackWithRetries: " + e);
            }
            Log.w(TAG, "registerCallbackWithRetries " + ii);
        }
        return -9;
    }

    public static void systemReady() {
        Log.i(TAG, "systemReady");
        if (mainHandler != null) {
            mainHandler.sendEmptyMessage(MainHandlerMessage.SYSTEM_READY);
        } else {
            Log.e(TAG, "systemReady: mainHandler is null");
        }
    }

    public static void registerCallbackForHalRecovery(int msDelay) {
        if (mainHandler != null) {
            SemInputDevice.setRecoveryState(true);
            mainHandler.sendEmptyMessageDelayed(MainHandlerMessage.HAL_FACTORY_REGISTER_CALLBACK, msDelay);
        }
    }

    private class MainHandler extends Handler implements MainHandlerMessage {
        MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HAL_FACTORY_REGISTER_CALLBACK:
                    int ret = SemInputDeviceManagerService.this.registerCallbackWithRetries(5);
                    if (ret < 0) {
                        sendEmptyMessageDelayed(HAL_FACTORY_REGISTER_CALLBACK, 100L);
                    } else {
                        SemInputDevice.setRecoveryState(false);
                    }
                    break;
                case SYSTEM_READY:
                    SemInputDeviceManagerService.this.commandService.getSupportDeviceList();
                    break;
                default:
                    Log.d(TAG, "MainHandler: " + msg);
                    break;
            }
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (pw == null) {
            return;
        }
        pw.println("dumping SemInputDeviceManagerService");
        pw.println("- hal version: " + this.sysinputHAL.getVersion());
        if (this.commandService != null) {
            pw.println("");
            this.commandService.dump(pw);
        }
        pw.println("end SemInputDeviceManagerService");
    }

    @Override
    public String getKeyPressStateAll() throws RemoteException {
        return this.commandService.getKeyPressStateAll();
    }

    @Override
    public boolean isKeyPressedByKeycode(int keycode) throws RemoteException {
        return this.commandService.isKeyPressedByKeycode(keycode);
    }

    @Override
    public int getSupportDevice(SemInputConstants.Device device) throws RemoteException {
        return this.commandService.getSupportDevice(device.toInt());
    }

    @Override
    public String getCommandList(SemInputConstants.Device device) throws RemoteException {
        return this.commandService.getCommandList(device.toInt());
    }

    @Override
    public int getTspSupportFeature(SemInputConstants.Device device) throws RemoteException {
        return this.commandService.getTspSupportFeature(device.toInt());
    }

    @Override
    public int getDeviceEnabled(SemInputConstants.Device device) throws RemoteException {
        try {
            String result = this.commandService.getProperty(device.toInt(), SemInputConstants.Property.ENABLED);
            if ("NG".equals(result)) {
                return -6;
            }
            if ("NA".equals(result)) {
                return -5;
            }
            return Integer.parseInt(result);
        } catch (Exception e) {
            Log.e(TAG, "getDeviceEnabled: " + e);
            return -7;
        }
    }

    @Override
    public String runCommand(SemInputConstants.Device device, String cmd) throws RemoteException {
        if (device == SemInputConstants.Device.CURRENT_TSP) {
            return this.commandService.runCommand(1, cmd);
        }
        return this.commandService.runCommand(device.toInt(), cmd);
    }

    @Override
    public int activate(SemInputConstants.Device device, SemInputConstants.DisplayState mode, boolean state) throws RemoteException {
        int devid = device.toInt();
        if (!Utilities.isDevidTsp(devid) && !Utilities.isDevidSpen(devid) && this.commandService.getSupportDevice(devid) == 0) {
            throw new SecurityException(device + " is not allowed");
        }
        if (mode != SemInputConstants.DisplayState.FORCE_ON && mode != SemInputConstants.DisplayState.FORCE_OFF) {
            throw new SecurityException(mode + " is not allowed");
        }
        String caller = getCallerClassName(4) + ":" + Process.myTid();
        return this.commandService.activate(devid, mode.toInt(), state, caller, true);
    }

    private String getCallerClassName(int stackIndex) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        String callerClassName = null;
        try {
            String callerClassName2 = stack[stackIndex].getClassName();
            String[] classNameSplit = callerClassName2.split("[.]");
            callerClassName = classNameSplit[classNameSplit.length - 1];
            return callerClassName.split("[$]")[0];
        } catch (Exception e) {
            if (callerClassName == null) {
                return "";
            }
            return callerClassName;
        }
    }

    @Override
    public int setTspEnabled(int devid, int mode, boolean state) throws RemoteException {
        if (!Utilities.isDevidTsp(devid)) {
            return -2;
        }
        return activate(SemInputConstants.Device.getFromInt(devid), SemInputConstants.DisplayState.getFromInt(mode), state);
    }

    @Override
    public int setTemperature(int value) throws RemoteException {
        return this.commandService.setPropertyAllTouch(SemInputConstants.Command.TEMPERATURE, value + "");
    }

    @Override
    public int setAodRect(int w, int h, int x, int y) throws RemoteException {
        return this.commandService.setProperty(1, SemInputConstants.Command.AOD_RECT, w + "," + h + "," + x + "," + y);
    }

    @Override
    public int setAodEnable(int mode) throws RemoteException {
        return this.commandService.setPropertyAllTouchAndSpen(SemInputConstants.Command.AOD, mode + "");
    }

    @Override
    public int setAotEnable(int value) {
        Log.i(TAG, "setAotEnable: " + value);
        return this.commandService.setProperty(1, SemInputConstants.Command.AOT, value + "");
    }

    @Override
    public int setFodEnable(int mode, int pressFast, int strictMode, int control) throws RemoteException {
        if (mode == 1) {
            return this.commandService.setPropertyAllTouch(SemInputConstants.Command.FOD, mode + "," + pressFast + "," + strictMode + "," + control);
        }
        return this.commandService.setPropertyAllTouch(SemInputConstants.Command.FOD, mode + "");
    }

    @Override
    public int setFodRect(int left, int top, int right, int bottom) throws RemoteException {
        return this.commandService.setProperty(1, SemInputConstants.Command.FOD_RECT, left + "," + top + "," + right + "," + bottom);
    }

    @Override
    public int setFodLpMode(int mode) throws RemoteException {
        return this.commandService.setPropertyAllTouch(SemInputConstants.Command.FOD_LP, mode + "");
    }

    @Override
    public int setSingletapEnable(int mode) throws RemoteException {
        return this.commandService.setPropertyAllTouch(SemInputConstants.Command.SINGLETAP, mode + "");
    }

    @Override
    public int setSyncChanged(int mode) throws RemoteException {
        return this.commandService.setPropertyAllTouch(SemInputConstants.Command.SYNC_CHANGED, mode + "");
    }

    @Override
    public int setSpenEnabled(int devid, int mode, boolean state) throws RemoteException {
        if (!Utilities.isDevidSpen(devid)) {
            return -2;
        }
        return activate(SemInputConstants.Device.getFromInt(devid), SemInputConstants.DisplayState.getFromInt(mode), state);
    }

    @Override
    public int setCommand(SemInputConstants.Device device, SemInputConstants.Command command, String mode) throws RemoteException {
        if (!command.isExternal()) {
            throw new SecurityException(command + " is not allowed");
        }
        switch (device) {
            case NOT_SPECIFIED:
                return this.commandService.setPropertyAllTouch(command, mode);
            case CURRENT_TSP:
                return this.commandService.setProperty(1, command, mode);
            default:
                return this.commandService.setProperty(device.toInt(), command, mode);
        }
    }

    @Override
    public int setProperty(SemInputConstants.Device device, SemInputConstants.Property property, String mode) throws RemoteException {
        if (!property.isExternalWrite()) {
            throw new SecurityException(property + " is not allowed");
        }
        return this.commandService.setProperty(device.toInt(), property, mode);
    }

    @Override
    public String getProperty(SemInputConstants.Device device, SemInputConstants.Property property) throws RemoteException {
        if (!property.isExternalRead()) {
            throw new SecurityException(property + " is not allowed");
        }
        return this.commandService.getProperty(device.toInt(), property);
    }
}
