/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.samsung.biometrics;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Switch;
import android.widget.TextView;

public class DebugActivity extends Activity
        implements DisplayBrightnessMonitor.OnBrightnessListener {

    private static final String TAG = "SB_DebugActivity";
    private static final float BRIGHTNESS_CONTROL_FACTOR = 1.0f; // same as MaskView

    private MaskView mMaskView;
    private DisplayBrightnessMonitor mBrightnessMonitor;
    private DisplaySolution mDisplaySolution;

    private TextView mStatBrightness;
    private TextView mStatAlpha;
    private TextView mStatArgb;
    private TextView mStatUdfpsBrightness;
    private TextView mStatSysfsPath;

    private float mUdfpsBrightness = 319.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        mBrightnessMonitor = DisplayBrightnessMonitor.getInstance();
        mDisplaySolution = new DisplaySolution(this);
        mMaskView = new MaskView(this);

        mUdfpsBrightness = mDisplaySolution.getUdfpsBrightness();
        mMaskView.setUdfpsBrightness(mUdfpsBrightness);

        mStatBrightness = findViewById(R.id.stat_brightness);
        mStatAlpha = findViewById(R.id.stat_alpha);
        mStatArgb = findViewById(R.id.stat_argb);
        mStatUdfpsBrightness = findViewById(R.id.stat_udfps_brightness);
        mStatSysfsPath = findViewById(R.id.stat_sysfs_path);

        mStatUdfpsBrightness.setText(String.valueOf(mUdfpsBrightness));
        mStatSysfsPath.setText(mBrightnessMonitor.getBrightnessFilePath());

        final Switch maskViewToggle = findViewById(R.id.mask_view_toggle);
        maskViewToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mMaskView.show();
                mMaskView.start();
            } else {
                mMaskView.hide();
                mMaskView.stop();
            }
        });

        final View root = findViewById(R.id.main);
        if (root != null) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }

        updateStats(mBrightnessMonitor.getCurrentBrightness());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBrightnessMonitor.registerListener(this);
        updateStats(mBrightnessMonitor.getCurrentBrightness());
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBrightnessMonitor.unregisterListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mMaskView != null) {
            mMaskView.hide();
            mMaskView.stop();
        }
    }

    @Override
    public void onBrightnessChanged(int brightness) {
        runOnUiThread(() -> updateStats(brightness));
    }

    private void updateStats(int brightness) {
        if (brightness < 0) {
            mStatBrightness.setText("unavailable");
            mStatAlpha.setText("—");
            mStatArgb.setText("—");
            return;
        }

        mStatBrightness.setText(String.valueOf(brightness));
        try {
            // Identical call to MaskView.updateBackgroundColor so the numbers match the overlay.
            float alpha = mDisplaySolution.getAlphaMaskLevel(brightness, mUdfpsBrightness,
                    BRIGHTNESS_CONTROL_FACTOR);
            int maskAlpha = Math.max(0, Math.min(255, (int) (255f * alpha)));
            mStatAlpha.setText(String.format("%.3f  (%d / 255)", alpha, maskAlpha));
            mStatArgb.setText(String.format("#%08X", maskAlpha << 24));
        } catch (RuntimeException e) {
            // e.g. brightness index outside the nits table
            Log.e(TAG, "Failed to compute alpha for brightness " + brightness, e);
            mStatAlpha.setText("error");
            mStatArgb.setText("error");
        }
    }
}
