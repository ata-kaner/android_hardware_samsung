package com.samsung.android.emergencymode;

import android.util.Log;

/** Simple log wrapper used by Samsung code. */
public final class Elog {
    private Elog() {}

    public static void d(String tag, String msg) { Log.d(tag, msg); }
    public static void i(String tag, String msg) { Log.i(tag, msg); }
    public static void v(String tag, String msg) { Log.v(tag, msg); }
    public static void w(String tag, String msg) { Log.w(tag, msg); }
    public static void e(String tag, String msg) { Log.e(tag, msg); }
}
