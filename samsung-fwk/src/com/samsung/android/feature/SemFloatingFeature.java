/*
 * Copyright (C) 2025-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.samsung.android.feature;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;

public final class SemFloatingFeature implements IFloatingFeature {

    private static final String TAG = "SemFloatingFeature";
    private static final String[] FEATURE_XML_PATHS = {
        "/system/etc/floating_feature.xml",
        "/system_ext/etc/floating_feature.xml",
        "/vendor/etc/floating_feature.xml",
    };

    private static final boolean DEFAULT_BOOLEAN_VALUE = false;
    private static final int DEFAULT_INT_VALUE = -1;
    private static final String DEFAULT_STRING_VALUE = "";

    private static final boolean LOG_ENABLED = false;
    private static final SemFloatingFeature sInstance;

    private final Hashtable<String, String> mFeatureList = new Hashtable<>();

    static {
        sInstance = new SemFloatingFeature();
    }

    private SemFloatingFeature() {
        try {
            loadFloatingFeature();
        } catch (Exception e) {
            loge(e);
        }
    }

    public static SemFloatingFeature getInstance() {
        return sInstance;
    }

    private static void logw(Object msg) {
        if (LOG_ENABLED) Log.w(TAG, String.valueOf(msg));
    }

    private static void loge(Object msg) {
        if (LOG_ENABLED) Log.e(TAG, String.valueOf(msg));
    }

    @Override
    public boolean getBoolean(String tag) {
        return getBoolean(tag, DEFAULT_BOOLEAN_VALUE);
    }

    @Override
    public boolean getBoolean(String tag, boolean defaultValue) {
        if (tag == null) {
            loge("getBoolean(): tag is null.");
            return defaultValue;
        }
        String val = mFeatureList.get(tag);
        return val != null ? Boolean.parseBoolean(val) : defaultValue;
    }

    @Override
    public String getString(String tag) {
        return getString(tag, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getString(String tag, String defaultValue) {
        if (tag == null) {
            loge("getString(): tag is null.");
            return defaultValue;
        }
        String val = mFeatureList.get(tag);
        return val != null ? val : defaultValue;
    }

    @Override
    public int getInt(String tag) {
        return getInt(tag, DEFAULT_INT_VALUE);
    }

    @Override
    public int getInt(String tag, int defaultValue) {
        if (tag == null) {
            loge("getInt(): tag is null.");
            return defaultValue;
        }
        String val = mFeatureList.get(tag);
        if (val == null) return defaultValue;

        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            loge("Value for " + tag + " cannot be parsed as int: " + val);
            return defaultValue;
        }
    }

    public int getInteger(String tag) {
        return getInt(tag);
    }

    public int getInteger(String tag, int defaultValue) {
        return getInt(tag, defaultValue);
    }

    private void loadFloatingFeature() throws XmlPullParserException, IOException {
        File file = null;
        for (String path : FEATURE_XML_PATHS) {
            File candidate = new File(path);
            if (candidate.exists() && candidate.canRead()) {
                file = candidate;
                break;
            }
        }
        if (file == null) {
            loge("No readable floating_feature.xml found");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);

            XmlPullParser parser = factory.newPullParser();
            parser.setInput(fis, "UTF-8");

            String currentTag = null;
            int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("feature".equals(tagName)) {
                        String name = parser.getAttributeValue(null, "name");
                        String value = parser.nextText();
                        if (name != null && value != null) {
                            mFeatureList.put(name.trim(), value.trim());
                        }
                        currentTag = null;
                    } else {
                        currentTag = tagName;
                    }
                } else if (eventType == XmlPullParser.TEXT && currentTag != null) {
                    String text = parser.getText();
                    if (text != null) {
                        String trimmed = text.trim();
                        if (!trimmed.isEmpty()) {
                            mFeatureList.put(currentTag, trimmed);
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    currentTag = null;
                }
                eventType = parser.next();
            }

            logw("Loaded " + mFeatureList.size() + " floating features from " + file.getPath());
        } catch (Exception e) {
            loge("Error parsing " + file.getPath() + ": " + e);
        }
    }
}
