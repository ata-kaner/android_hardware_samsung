package android.os;

import android.os.Build;

/**
 * Stub for Samsung SemHqmManager on AOSP/Lineage.
 *
 * HQM (Hardware Quality Monitor) is Samsung-specific. On non-Samsung builds we
 * report disabled and make send* calls no-op that return false.
 */
public class SemHqmManager {
    private static final boolean DEBUG = "eng".equals(Build.TYPE);

    private static final Object BDlock = new Object();
    private static final Object BDlock_sys = new Object();

    Handler mHandler;
    ISemHqmManager mService;

    public SemHqmManager(ISemHqmManager service, Handler handler) {
        this.mService = service;
        this.mHandler = handler;
    }

    public boolean sendHWParamServer(int a, String b, String c, String d, String e,
                                     String f, String g, String h) {
        if (mService == null) return false;
        synchronized (BDlock) {
            try {
                return mService.sendHWParamServer(a, b, c, d, e, f, g, h);
            } catch (Exception ex) {
                printExceptionTrace(ex);
                return false;
            }
        }
    }

    public boolean sendHWParamToHQM(int a, String b, String c, String d, String e,
                                    String f, String g, String h, String i) {
        if (mService == null) return false;
        synchronized (BDlock) {
            try {
                return mService.sendHWParamToHQM(a, b, c, d, e, f, g, h, i);
            } catch (Exception ex) {
                printExceptionTrace(ex);
                return false;
            }
        }
    }

    public boolean sendHWParamToHQMwithAppId(int a, String b, String c, String d, String e,
                                             String f, String g, String h, String i, String j) {
        if (mService == null) return false;
        synchronized (BDlock) {
            try {
                return mService.sendHWParamToHQMwithAppId(a, b, c, d, e, f, g, h, i, j);
            } catch (Exception ex) {
                printExceptionTrace(ex);
                return false;
            }
        }
    }

    public boolean sendHWParamToHQMwithFile(int a, String b, String c, String d, String e,
                                           String f, String g, String h, String i, String j, String k) {
        if (mService == null) return false;
        synchronized (BDlock) {
            try {
                return mService.sendHWParamToHQMwithFile(a, b, c, d, e, f, g, h, i, j, k);
            } catch (Exception ex) {
                printExceptionTrace(ex);
                return false;
            }
        }
    }

    public void sendSystemInfoToHQM(int a, String b, String c) {
        if (mService == null) return;
        synchronized (BDlock_sys) {
            try {
                mService.sendSystemInfoToHQM(a, b, c);
            } catch (Exception ex) {
                printExceptionTrace(ex);
            }
        }
    }

    public boolean getHqmEnable() {
        if (mService == null) return false;
        synchronized (BDlock) {
            try {
                return mService.getHqmEnable();
            } catch (Exception ex) {
                printExceptionTrace(ex);
                return false;
            }
        }
    }

    public boolean getDVServerEnable() {
        if (mService == null) return false;
        synchronized (BDlock) {
            try {
                return mService.getDVServerEnable();
            } catch (Exception ex) {
                printExceptionTrace(ex);
                return false;
            }
        }
    }

    public boolean getCFServerEnable() {
        if (mService == null) return false;
        synchronized (BDlock) {
            try {
                return mService.getCFServerEnable();
            } catch (Exception ex) {
                printExceptionTrace(ex);
                return false;
            }
        }
    }

    private static void printExceptionTrace(Exception exc) {
        if (DEBUG) exc.printStackTrace();
    }
}
