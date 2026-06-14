package com.samsung.android.hardware.secinputdev;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.samsung.android.hardware.secinputdev.utils.SemInputConstants;

public interface ISemInputDeviceManager extends IInterface {
    public static final String DESCRIPTOR = "com.samsung.android.hardware.secinputdev.ISemInputDeviceManager";

    int activate(SemInputConstants.Device device, SemInputConstants.DisplayState displayState, boolean z) throws RemoteException;

    String getCommandList(SemInputConstants.Device device) throws RemoteException;

    int getDeviceEnabled(SemInputConstants.Device device) throws RemoteException;

    String getKeyPressStateAll() throws RemoteException;

    String getProperty(SemInputConstants.Device device, SemInputConstants.Property property) throws RemoteException;

    int getSupportDevice(SemInputConstants.Device device) throws RemoteException;

    int getTspSupportFeature(SemInputConstants.Device device) throws RemoteException;

    boolean isKeyPressedByKeycode(int i) throws RemoteException;

    String runCommand(SemInputConstants.Device device, String str) throws RemoteException;

    int setAodEnable(int i) throws RemoteException;

    int setAodRect(int i, int i2, int i3, int i4) throws RemoteException;

    int setAotEnable(int i) throws RemoteException;

    int setCommand(SemInputConstants.Device device, SemInputConstants.Command command, String str) throws RemoteException;

    int setFodEnable(int i, int i2, int i3, int i4) throws RemoteException;

    int setFodLpMode(int i) throws RemoteException;

    int setFodRect(int i, int i2, int i3, int i4) throws RemoteException;

    int setProperty(SemInputConstants.Device device, SemInputConstants.Property property, String str) throws RemoteException;

    int setSingletapEnable(int i) throws RemoteException;

    int setSpenEnabled(int i, int i2, boolean z) throws RemoteException;

    int setSyncChanged(int i) throws RemoteException;

    int setTemperature(int i) throws RemoteException;

    int setTspEnabled(int i, int i2, boolean z) throws RemoteException;

    public static class Default implements ISemInputDeviceManager {
        @Override
        public String getKeyPressStateAll() throws RemoteException {
            return null;
        }

        @Override
        public boolean isKeyPressedByKeycode(int keycode) throws RemoteException {
            return false;
        }

        @Override
        public int getSupportDevice(SemInputConstants.Device device) throws RemoteException {
            return 0;
        }

        @Override
        public int getTspSupportFeature(SemInputConstants.Device device) throws RemoteException {
            return 0;
        }

        @Override
        public int getDeviceEnabled(SemInputConstants.Device device) throws RemoteException {
            return 0;
        }

        @Override
        public String getCommandList(SemInputConstants.Device device) throws RemoteException {
            return null;
        }

        @Override
        public int setTspEnabled(int devid, int mode, boolean state) throws RemoteException {
            return 0;
        }

        @Override
        public int setTemperature(int value) throws RemoteException {
            return 0;
        }

        @Override
        public int setAodRect(int w, int h, int x, int y) throws RemoteException {
            return 0;
        }

        @Override
        public int setAodEnable(int mode) throws RemoteException {
            return 0;
        }

        @Override
        public int setAotEnable(int mode) throws RemoteException {
            return 0;
        }

        @Override
        public int setFodEnable(int mode, int pressFast, int strictMode, int control) throws RemoteException {
            return 0;
        }

        @Override
        public int setFodRect(int left, int top, int right, int bottom) throws RemoteException {
            return 0;
        }

        @Override
        public int setFodLpMode(int mode) throws RemoteException {
            return 0;
        }

        @Override
        public int setSingletapEnable(int mode) throws RemoteException {
            return 0;
        }

        @Override
        public int setSyncChanged(int mode) throws RemoteException {
            return 0;
        }

        @Override
        public int setSpenEnabled(int devid, int mode, boolean state) throws RemoteException {
            return 0;
        }

        @Override
        public int activate(SemInputConstants.Device device, SemInputConstants.DisplayState mode, boolean state) throws RemoteException {
            return 0;
        }

        @Override
        public int setCommand(SemInputConstants.Device device, SemInputConstants.Command command, String mode) throws RemoteException {
            return 0;
        }

        @Override
        public int setProperty(SemInputConstants.Device device, SemInputConstants.Property property, String mode) throws RemoteException {
            return 0;
        }

        @Override
        public String getProperty(SemInputConstants.Device device, SemInputConstants.Property property) throws RemoteException {
            return null;
        }

        @Override
        public String runCommand(SemInputConstants.Device device, String cmd) throws RemoteException {
            return null;
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements ISemInputDeviceManager {
        static final int TRANSACTION_getKeyPressStateAll = 1;
        static final int TRANSACTION_isKeyPressedByKeycode = 2;
        static final int TRANSACTION_getSupportDevice = 11;
        static final int TRANSACTION_getTspSupportFeature = 12;
        static final int TRANSACTION_getDeviceEnabled = 13;
        static final int TRANSACTION_getCommandList = 14;
        static final int TRANSACTION_setTspEnabled = 15;
        static final int TRANSACTION_setTemperature = 16;
        static final int TRANSACTION_setAodRect = 17;
        static final int TRANSACTION_setAodEnable = 18;
        static final int TRANSACTION_setAotEnable = 19;
        static final int TRANSACTION_setFodEnable = 20;
        static final int TRANSACTION_setFodRect = 21;
        static final int TRANSACTION_setFodLpMode = 22;
        static final int TRANSACTION_setSingletapEnable = 23;
        static final int TRANSACTION_setSyncChanged = 24;
        static final int TRANSACTION_setSpenEnabled = 25;
        static final int TRANSACTION_activate = 26;
        static final int TRANSACTION_setCommand = 27;
        static final int TRANSACTION_setProperty = 28;
        static final int TRANSACTION_getProperty = 29;
        static final int TRANSACTION_runCommand = 30;

        public Stub() {
            attachInterface(this, ISemInputDeviceManager.DESCRIPTOR);
        }

        public static ISemInputDeviceManager asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(ISemInputDeviceManager.DESCRIPTOR);
            if (iin != null && (iin instanceof ISemInputDeviceManager)) {
                return (ISemInputDeviceManager) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        public static String getDefaultTransactionName(int transactionCode) {
            switch (transactionCode) {
                case TRANSACTION_getKeyPressStateAll:
                    return "getKeyPressStateAll";
                case TRANSACTION_isKeyPressedByKeycode:
                    return "isKeyPressedByKeycode";
                case TRANSACTION_getSupportDevice:
                    return "getSupportDevice";
                case TRANSACTION_getTspSupportFeature:
                    return "getTspSupportFeature";
                case TRANSACTION_getDeviceEnabled:
                    return "getDeviceEnabled";
                case TRANSACTION_getCommandList:
                    return "getCommandList";
                case TRANSACTION_setTspEnabled:
                    return "setTspEnabled";
                case TRANSACTION_setTemperature:
                    return "setTemperature";
                case TRANSACTION_setAodRect:
                    return "setAodRect";
                case TRANSACTION_setAodEnable:
                    return "setAodEnable";
                case TRANSACTION_setAotEnable:
                    return "setAotEnable";
                case TRANSACTION_setFodEnable:
                    return "setFodEnable";
                case TRANSACTION_setFodRect:
                    return "setFodRect";
                case TRANSACTION_setFodLpMode:
                    return "setFodLpMode";
                case TRANSACTION_setSingletapEnable:
                    return "setSingletapEnable";
                case TRANSACTION_setSyncChanged:
                    return "setSyncChanged";
                case TRANSACTION_setSpenEnabled:
                    return "setSpenEnabled";
                case TRANSACTION_activate:
                    return "activate";
                case TRANSACTION_setCommand:
                    return "setCommand";
                case TRANSACTION_setProperty:
                    return "setProperty";
                case TRANSACTION_getProperty:
                    return "getProperty";
                case TRANSACTION_runCommand:
                    return "runCommand";
                default:
                    return null;
            }
        }

        public String getTransactionName(int transactionCode) {
            return getDefaultTransactionName(transactionCode);
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code >= 1 && code <= 16777215) {
                data.enforceInterface(ISemInputDeviceManager.DESCRIPTOR);
            }
            if (code == 1598968902) {
                reply.writeString(ISemInputDeviceManager.DESCRIPTOR);
                return true;
            }
            switch (code) {
                case TRANSACTION_getKeyPressStateAll: {
                    String _result = getKeyPressStateAll();
                    reply.writeNoException();
                    reply.writeString(_result);
                    return true;
                }
                case TRANSACTION_isKeyPressedByKeycode: {
                    int _arg0 = data.readInt();
                    data.enforceNoDataAvail();
                    boolean _result = isKeyPressedByKeycode(_arg0);
                    reply.writeNoException();
                    reply.writeBoolean(_result);
                    return true;
                }
                case TRANSACTION_getSupportDevice: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    data.enforceNoDataAvail();
                    int _result = getSupportDevice(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_getTspSupportFeature: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    data.enforceNoDataAvail();
                    int _result = getTspSupportFeature(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_getDeviceEnabled: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    data.enforceNoDataAvail();
                    int _result = getDeviceEnabled(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_getCommandList: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    data.enforceNoDataAvail();
                    String _result = getCommandList(_arg0);
                    reply.writeNoException();
                    reply.writeString(_result);
                    return true;
                }
                case TRANSACTION_setTspEnabled: {
                    int _arg0 = data.readInt();
                    int _arg1 = data.readInt();
                    boolean _arg2 = data.readBoolean();
                    data.enforceNoDataAvail();
                    int _result = setTspEnabled(_arg0, _arg1, _arg2);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setTemperature: {
                    int _arg0 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setTemperature(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setAodRect: {
                    int _arg0 = data.readInt();
                    int _arg1 = data.readInt();
                    int _arg2 = data.readInt();
                    int _arg3 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setAodRect(_arg0, _arg1, _arg2, _arg3);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setAodEnable: {
                    int _arg0 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setAodEnable(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setAotEnable: {
                    int _arg0 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setAotEnable(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setFodEnable: {
                    int _arg0 = data.readInt();
                    int _arg1 = data.readInt();
                    int _arg2 = data.readInt();
                    int _arg3 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setFodEnable(_arg0, _arg1, _arg2, _arg3);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setFodRect: {
                    int _arg0 = data.readInt();
                    int _arg1 = data.readInt();
                    int _arg2 = data.readInt();
                    int _arg3 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setFodRect(_arg0, _arg1, _arg2, _arg3);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setFodLpMode: {
                    int _arg0 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setFodLpMode(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setSingletapEnable: {
                    int _arg0 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setSingletapEnable(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setSyncChanged: {
                    int _arg0 = data.readInt();
                    data.enforceNoDataAvail();
                    int _result = setSyncChanged(_arg0);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setSpenEnabled: {
                    int _arg0 = data.readInt();
                    int _arg1 = data.readInt();
                    boolean _arg2 = data.readBoolean();
                    data.enforceNoDataAvail();
                    int _result = setSpenEnabled(_arg0, _arg1, _arg2);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_activate: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    SemInputConstants.DisplayState _arg1 = (SemInputConstants.DisplayState) data.readTypedObject(SemInputConstants.DisplayState.CREATOR);
                    boolean _arg2 = data.readBoolean();
                    data.enforceNoDataAvail();
                    int _result = activate(_arg0, _arg1, _arg2);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setCommand: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    SemInputConstants.Command _arg1 = (SemInputConstants.Command) data.readTypedObject(SemInputConstants.Command.CREATOR);
                    String _arg2 = data.readString();
                    data.enforceNoDataAvail();
                    int _result = setCommand(_arg0, _arg1, _arg2);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_setProperty: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    SemInputConstants.Property _arg1 = (SemInputConstants.Property) data.readTypedObject(SemInputConstants.Property.CREATOR);
                    String _arg2 = data.readString();
                    data.enforceNoDataAvail();
                    int _result = setProperty(_arg0, _arg1, _arg2);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_getProperty: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    SemInputConstants.Property _arg1 = (SemInputConstants.Property) data.readTypedObject(SemInputConstants.Property.CREATOR);
                    data.enforceNoDataAvail();
                    String _result = getProperty(_arg0, _arg1);
                    reply.writeNoException();
                    reply.writeString(_result);
                    return true;
                }
                case TRANSACTION_runCommand: {
                    SemInputConstants.Device _arg0 = (SemInputConstants.Device) data.readTypedObject(SemInputConstants.Device.CREATOR);
                    String _arg1 = data.readString();
                    data.enforceNoDataAvail();
                    String _result = runCommand(_arg0, _arg1);
                    reply.writeNoException();
                    reply.writeString(_result);
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ISemInputDeviceManager {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return ISemInputDeviceManager.DESCRIPTOR;
            }

            @Override
            public String getKeyPressStateAll() throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    this.mRemote.transact(TRANSACTION_getKeyPressStateAll, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readString();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public boolean isKeyPressedByKeycode(int keycode) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(keycode);
                    this.mRemote.transact(TRANSACTION_isKeyPressedByKeycode, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readBoolean();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int getSupportDevice(SemInputConstants.Device device) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    this.mRemote.transact(TRANSACTION_getSupportDevice, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int getTspSupportFeature(SemInputConstants.Device device) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    this.mRemote.transact(TRANSACTION_getTspSupportFeature, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int getDeviceEnabled(SemInputConstants.Device device) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    this.mRemote.transact(TRANSACTION_getDeviceEnabled, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public String getCommandList(SemInputConstants.Device device) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    this.mRemote.transact(TRANSACTION_getCommandList, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readString();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setTspEnabled(int devid, int mode, boolean state) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(devid);
                    _data.writeInt(mode);
                    _data.writeBoolean(state);
                    this.mRemote.transact(TRANSACTION_setTspEnabled, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setTemperature(int value) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(value);
                    this.mRemote.transact(TRANSACTION_setTemperature, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setAodRect(int w, int h, int x, int y) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(w);
                    _data.writeInt(h);
                    _data.writeInt(x);
                    _data.writeInt(y);
                    this.mRemote.transact(TRANSACTION_setAodRect, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setAodEnable(int mode) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(mode);
                    this.mRemote.transact(TRANSACTION_setAodEnable, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setAotEnable(int mode) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(mode);
                    this.mRemote.transact(TRANSACTION_setAotEnable, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setFodEnable(int mode, int pressFast, int strictMode, int control) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(mode);
                    _data.writeInt(pressFast);
                    _data.writeInt(strictMode);
                    _data.writeInt(control);
                    this.mRemote.transact(TRANSACTION_setFodEnable, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setFodRect(int left, int top, int right, int bottom) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(left);
                    _data.writeInt(top);
                    _data.writeInt(right);
                    _data.writeInt(bottom);
                    this.mRemote.transact(TRANSACTION_setFodRect, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setFodLpMode(int mode) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(mode);
                    this.mRemote.transact(TRANSACTION_setFodLpMode, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setSingletapEnable(int mode) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(mode);
                    this.mRemote.transact(TRANSACTION_setSingletapEnable, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setSyncChanged(int mode) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(mode);
                    this.mRemote.transact(TRANSACTION_setSyncChanged, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setSpenEnabled(int devid, int mode, boolean state) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeInt(devid);
                    _data.writeInt(mode);
                    _data.writeBoolean(state);
                    this.mRemote.transact(TRANSACTION_setSpenEnabled, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int activate(SemInputConstants.Device device, SemInputConstants.DisplayState mode, boolean state) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    _data.writeTypedObject(mode, 0);
                    _data.writeBoolean(state);
                    this.mRemote.transact(TRANSACTION_activate, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setCommand(SemInputConstants.Device device, SemInputConstants.Command command, String mode) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    _data.writeTypedObject(command, 0);
                    _data.writeString(mode);
                    this.mRemote.transact(TRANSACTION_setCommand, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int setProperty(SemInputConstants.Device device, SemInputConstants.Property property, String mode) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    _data.writeTypedObject(property, 0);
                    _data.writeString(mode);
                    this.mRemote.transact(TRANSACTION_setProperty, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public String getProperty(SemInputConstants.Device device, SemInputConstants.Property property) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    _data.writeTypedObject(property, 0);
                    this.mRemote.transact(TRANSACTION_getProperty, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readString();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public String runCommand(SemInputConstants.Device device, String cmd) throws RemoteException {
                Parcel _data = Parcel.obtain(asBinder());
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ISemInputDeviceManager.DESCRIPTOR);
                    _data.writeTypedObject(device, 0);
                    _data.writeString(cmd);
                    this.mRemote.transact(TRANSACTION_runCommand, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readString();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public int getMaxTransactionId() {
            return 30;
        }
    }
}
