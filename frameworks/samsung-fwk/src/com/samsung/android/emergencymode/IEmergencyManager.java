package com.samsung.android.emergencymode;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/**
 * Minimal Binder-style stub to satisfy references.
 * Not a functional service.
 */
public interface IEmergencyManager extends IInterface {

    int getEmergencyState() throws RemoteException;
    boolean checkValidIntentAction(String action, String pkg) throws RemoteException;
    boolean checkInvalidProcess(String processName) throws RemoteException;
    boolean checkInvalidBroadcast(String action, String senderPkg) throws RemoteException;
    boolean needMobileDataBlock() throws RemoteException;
    boolean isScreenOn() throws RemoteException;
    void setUserPackageBlocked(boolean blocked) throws RemoteException;
    boolean isUserPackageBlocked() throws RemoteException;
    boolean isModifying() throws RemoteException;
    boolean checkModeType(int modeType) throws RemoteException;

    abstract class Stub extends Binder implements IEmergencyManager {
        public static IEmergencyManager asInterface(IBinder obj) {
            return null;
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
