/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full-screen black overlay shown immediately after optical fingerprint authentication
 * succeeds from Screen-Off or AOD (Always-On Display).
 *
 * <p>Matches Samsung OneUI's {@code UdfpsOpticalBlackMaskWindow} / {@code BlackMaskView} behavior.
 * When unlocking from AOD/Screen-Off, the optical illumination circle turns off, but the display
 * panel requires ~50-100 ms to transition hardware refresh rates and step down from HBM to ambient
 * user brightness. This black mask hides transient panel flicker, AOD clock artifacts, and the
 * brightness overshoot until interactive brightness settles.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link #show()}: Adds black overlay window and observes {@link DisplayBrightnessMonitor}.
 *   <li>{@link #onBrightnessChanged(int)}: Hides the overlay as soon as the panel settles at interactive brightness.
 *   <li>Safety timeout (100 ms): Hides the overlay if no brightness transition event is received.
 * </ul>
 */
public final class BlackMaskView implements DisplayBrightnessMonitor.OnBrightnessListener {

    private static final String TAG = "SB_BlackMaskView";
    private static final long TIMEOUT_MS = 100L;

    private final Context mContext;
    private final WindowManager mWm;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final DisplayBrightnessMonitor mBrightnessMonitor;
    private final AtomicBoolean mShowing = new AtomicBoolean(false);

    private View mRoot;
    private int mInitialBrightness = -1;

    private final Runnable mTimeoutRunnable = this::hide;

    public BlackMaskView(Context context) {
        mContext = context;
        mWm = context.getSystemService(WindowManager.class);
        mBrightnessMonitor = DisplayBrightnessMonitor.getInstance();
    }

    /**
     * Shows the full-screen black overlay.
     * Safe to call multiple times; redundant calls while showing are ignored.
     */
    public void show() {
        if (!mShowing.compareAndSet(false, true)) {
            Log.d(TAG, "show: already showing");
            return;
        }

        mInitialBrightness = mBrightnessMonitor.getCurrentBrightness();

        mMain.post(() -> {
            ensureView();
            try {
                mWm.addView(mRoot, buildLayoutParams());
                mBrightnessMonitor.registerListener(this);
                mMain.postDelayed(mTimeoutRunnable, TIMEOUT_MS);
                Log.i(TAG, "show: black mask visible (initialBrightness=" + mInitialBrightness + ")");
            } catch (Exception e) {
                Log.e(TAG, "Failed to add black mask view to WindowManager", e);
                mShowing.set(false);
            }
        });
    }

    /**
     * Hides the full-screen black overlay.
     */
    public void hide() {
        if (!mShowing.compareAndSet(true, false)) return;
        mMain.post(() -> {
            mMain.removeCallbacks(mTimeoutRunnable);
            mBrightnessMonitor.unregisterListener(this);
            if (mRoot != null) {
                try {
                    mWm.removeViewImmediate(mRoot);
                } catch (Exception e) {
                    Log.w(TAG, "hide: removeView failed: " + e.getMessage());
                } finally {
                    mRoot = null;
                }
            }
            Log.i(TAG, "hide: black mask removed");
        });
    }

    public boolean isShowing() {
        return mShowing.get();
    }

    @Override
    public void onBrightnessChanged(int brightness) {
        // Dismiss as soon as a new interactive brightness level is reported,
        // signaling that the display has transitioned away from AOD/HBM.
        if (mInitialBrightness >= 0 && brightness != mInitialBrightness) {
            Log.d(TAG, "onBrightnessChanged: " + mInitialBrightness + " -> " + brightness
                    + " — display settled, hiding black mask");
            hide();
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void ensureView() {
        if (mRoot != null) return;
        mRoot = new FrameLayout(mContext);
        mRoot.setBackgroundColor(Color.BLACK);
        mRoot.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        final int flags = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY,
                flags,
                PixelFormat.OPAQUE);

        lp.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        lp.privateFlags =
                WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
                | WindowManager.LayoutParams.PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY;
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        lp.setTitle("FP BlackMaskView");
        return lp;
    }
}
