/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Optical sensors: full-screen dim overlay window plus a separate window holding the white
 * light-source circle (see the layer-name note on CIRCLE_WINDOW_TITLE).
 */
public final class MaskView implements DisplayBrightnessMonitor.OnBrightnessListener {
    private static final String TAG = "SB_MaskView";

    // SurfaceFlinger (UdfpsExtension / OutputLayer) picks z-order by layer name:
    //  - contains "Dim Layer for UDFPS"                     -> dim z-order
    //  - contains " SurfaceView[UdfpsControllerOverlay](BLAST)" -> "touched" z-order
    // A SurfaceView's layer is named "SurfaceView[<window title>](BLAST)", so the circle needs
    // its own window with this title, and must never be in a window whose title contains the
    // dim layer name.
    private static final String DIM_WINDOW_TITLE = "Dim Layer for UDFPS";
    private static final String CIRCLE_WINDOW_TITLE = "UdfpsControllerOverlay";
    private static final int DEFAULT_BRIGHTNESS = 127;
    private static final float BRIGHTNESS_CONTROL_FACTOR = 1.0f;
    private static final float MAX_ALPHA = 0.93f; // Matches Samsung OneUI UdfpsMaskWindow

    private final Context mContext;
    private final WindowManager mWm;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final DisplayBrightnessMonitor mDisplayBrightnessMonitor;
    private final DisplaySolution mDisplaySolution;

    private DisplayStateListener mDisplayStateListener;
    private FrameLayout mRoot;
    private FrameLayout mCircleRoot;
    private View mDimView;
    private LightSourceView mLightSourceView;
    private FingerprintSensorInfo mSensorInfo;
    private boolean mAdded;
    private float mUdfpsBrightness;

    public MaskView(Context context) {
        mContext = context;
        mWm = context.getSystemService(WindowManager.class);
        mDisplayBrightnessMonitor = DisplayBrightnessMonitor.getInstance();
        mDisplayBrightnessMonitor.init(context);
        mDisplaySolution = new DisplaySolution(context);
    }

    public void setDisplayStateListener(DisplayStateListener listener) {
        mDisplayStateListener = listener;
    }

    public void setUdfpsBrightness(float brightness) {
        mUdfpsBrightness = brightness;
    }

    public DisplaySolution getDisplaySolution() {
        return mDisplaySolution;
    }

    /**
     * May be called before the view has ever been inflated; the info is kept and applied when
     * the layout is created.
     */
    public void setSensorInfo(FingerprintSensorInfo info) {
        mMain.post(() -> {
            mSensorInfo = info;
            if (mLightSourceView != null) mLightSourceView.setSensorInfo(info);
        });
    }

    public void start() {
        mDisplayBrightnessMonitor.registerListener(this);
    }

    public void stop() {
        mDisplayBrightnessMonitor.unregisterListener(this);
    }

    @Override
    public void onBrightnessChanged(int brightness) {
        updateBackgroundColor(brightness);
    }

    public void showCircle() {
        mMain.post(() -> {
            updateBackgroundColor(-1);
            if (mDimView != null) {
                mDimView.setVisibility(View.VISIBLE);
            }
            if (mLightSourceView != null) {
                mLightSourceView.show();
            }
        });
    }

    public void hideCircle() {
        mMain.post(() -> {
            if (mLightSourceView != null) {
                mLightSourceView.hide();
            }
            if (mDimView != null) {
                mDimView.setVisibility(View.INVISIBLE);
            }
        });
    }

    public void show() {
        mMain.post(() -> {
            if (mAdded) return;
            ensureView();
            updateBackgroundColor(-1);
            try {
                // Dim first, circle second: later windows of the same type sit on top.
                mWm.addView(mRoot, buildLayoutParams(DIM_WINDOW_TITLE));
                mWm.addView(mCircleRoot, buildLayoutParams(CIRCLE_WINDOW_TITLE));
                mAdded = true;
                Log.d(TAG, "MaskView added to WindowManager");
            } catch (Exception e) {
                Log.e(TAG, "Failed to add mask view to WindowManager", e);
            }
        });
    }

    public void hide() {
        mMain.post(() -> {
            if (!mAdded) return;
            if (mLightSourceView != null) {
                mLightSourceView.hide();
            }
            if (mDimView != null) {
                mDimView.setVisibility(View.INVISIBLE);
            }
            try {
                mWm.removeViewImmediate(mCircleRoot);
            } catch (RuntimeException e) {
                Log.w(TAG, "removing circle window failed", e);
            }
            try {
                mWm.removeViewImmediate(mRoot);
            } catch (RuntimeException e) {
                Log.w(TAG, "removing dim window failed", e);
            } finally {
                mAdded = false;
            }
            Log.d(TAG, "MaskView removed from WindowManager");
        });
    }

    public boolean isShowing() {
        return mAdded;
    }

    private void ensureView() {
        if (mRoot != null) return;

        mRoot = (FrameLayout) LayoutInflater.from(mContext)
                .inflate(R.layout.fingerprint_maskview, null);
        mDimView = mRoot.findViewById(R.id.fingerprint_maskview);
        mDimView.setVisibility(View.VISIBLE);
        mRoot.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mRoot.setForceDarkAllowed(false);

        mCircleRoot = (FrameLayout) LayoutInflater.from(mContext)
                .inflate(R.layout.fingerprint_lightsource, null);
        mLightSourceView = mCircleRoot.findViewById(R.id.fingerprint_lightsource_surfaceview);
        mCircleRoot.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mCircleRoot.setForceDarkAllowed(false);

        if (mSensorInfo != null && mLightSourceView != null) {
            mLightSourceView.setSensorInfo(mSensorInfo);
        }
    }

    private WindowManager.LayoutParams buildLayoutParams(String title) {
        final int flags = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);

        lp.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        lp.privateFlags =
                WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
                        | WindowManager.LayoutParams.PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY;
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        lp.setTitle(title);
        return lp;
    }

    public void updateBackgroundColor(int displayBrightness) {
        if (mDimView == null) return;

        if (displayBrightness < 0) {
            if (mDisplayStateListener != null && mDisplayStateListener.isDoze()) {
                // In AoD, use AoD backlight level (2 nits) for dim calculation
                float aodBacklight = mDisplaySolution.getFingerPrintBacklightValue(2);
                if (aodBacklight > 0) {
                    displayBrightness = Math.round(aodBacklight);
                } else {
                    displayBrightness = mDisplayBrightnessMonitor.getCurrentBrightness();
                }
            } else {
                displayBrightness = mDisplayBrightnessMonitor.getCurrentBrightness();
            }
        }
        if (displayBrightness < 0) {
            displayBrightness = DEFAULT_BRIGHTNESS;
        }

        float alpha = mDisplaySolution.getAlphaMaskLevel(displayBrightness, mUdfpsBrightness,
                BRIGHTNESS_CONTROL_FACTOR);
        float clampedAlpha = Math.min(MAX_ALPHA, Math.max(0.0f, alpha));
        int maskAlpha = (int) (255f * clampedAlpha);
        int argb = maskAlpha << 24;

        Log.i(TAG, "updateBackgroundColor: " + Integer.toHexString(argb) + ", "
                + mUdfpsBrightness + ", " + maskAlpha);
        mDimView.setBackgroundColor(argb);
    }
}
