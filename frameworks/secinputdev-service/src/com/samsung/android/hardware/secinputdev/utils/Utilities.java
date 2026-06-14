package com.samsung.android.hardware.secinputdev.utils;

import android.util.Log;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Utilities {
    private static final String TAG = "SemInput:Utlities";

    public static void loggingException(String tag, String word, Exception e) {
        Log.e(tag, word + ": " + e);
    }

    public static void loggingThrowable(String tag, String word, Throwable e) {
        Log.e(tag, word + ": " + e);
    }

    public static boolean isDevidTsp(int devid) {
        if (devid >= 1 && devid < 3) {
            return true;
        }
        return false;
    }

    public static boolean isDevidSpen(int devid) {
        if (devid == 11) {
            return true;
        }
        return false;
    }

    public static boolean isDevidKeyboard(int devid) {
        if (devid == 31) {
            return true;
        }
        return false;
    }

    public static class Result {
        private final Lock lock = new ReentrantLock();
        private final Condition updated = this.lock.newCondition();
        private int resultInteger = -20;
        private String resultString = "NG";
        private boolean isUpdated = false;

        public int getInteger() {
            waitUpdate();
            return this.resultInteger;
        }

        public String getString() {
            waitUpdate();
            return this.resultString;
        }

        public void set(int result) {
            this.lock.lock();
            try {
                this.resultInteger = result;
                this.isUpdated = true;
                this.updated.signalAll();
            } finally {
                this.lock.unlock();
            }
        }

        public void set(String result) {
            this.lock.lock();
            try {
                this.resultString = result;
                this.isUpdated = true;
                this.updated.signalAll();
            } finally {
                this.lock.unlock();
            }
        }

        private void waitUpdate() {
            this.lock.lock();
            while (!this.isUpdated) {
                try {
                    try {
                        this.updated.await();
                    } catch (InterruptedException e) {
                        Log.d(Utilities.TAG, "waitUpdate: interrupted");
                    }
                } finally {
                    this.lock.unlock();
                }
            }
        }
    }
}
