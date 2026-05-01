package android.os;

import android.os.IInterface;
import android.os.RemoteException;

/**
 * Minimal stub for Samsung ISemHqmManager binder interface.
 * Not a functional service.
 */
public interface ISemHqmManager extends IInterface {
    boolean sendHWParamServer(int a, String b, String c, String d, String e,
                              String f, String g, String h) throws RemoteException;

    boolean sendHWParamToHQM(int a, String b, String c, String d, String e,
                             String f, String g, String h, String i) throws RemoteException;

    boolean sendHWParamToHQMwithAppId(int a, String b, String c, String d, String e,
                                      String f, String g, String h, String i, String j) throws RemoteException;

    boolean sendHWParamToHQMwithFile(int a, String b, String c, String d, String e,
                                     String f, String g, String h, String i, String j, String k) throws RemoteException;

    void sendSystemInfoToHQM(int a, String b, String c) throws RemoteException;

    boolean getHqmEnable() throws RemoteException;
    boolean getDVServerEnable() throws RemoteException;
    boolean getCFServerEnable() throws RemoteException;
}
