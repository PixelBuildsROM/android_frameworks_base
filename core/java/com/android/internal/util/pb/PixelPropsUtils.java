/*
 * Copyright (C) 2022 The Pixel Experience Project
 *               2021-2022 crDroid Android Project
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *           (C) 2024 PixelBuildsROM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.pb;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final String DEVICE = SystemProperties.get("ro.build.version.device");
    private static final String MODEL = SystemProperties.get("ro.product.model", Build.MODEL);

    private static final String PACKAGE_PHOTOS = "com.google.android.apps.photos";

    private static final boolean DEBUG = SystemProperties.getBoolean("ro.debug.pixelpropsutils", false);

    private static String[] sCertifiedProps =
    Resources.getSystem().getStringArray(R.array.config_certifiedBuildProperties);

    private static final Map<String, Object> propsToChangePixel;
    private static final Map<String, Object> propsToSpoofPhotos;

    private static final ArrayList<String> packagesToChangePixel = 
    new ArrayList<String> (
        Arrays.asList(
            "com.google.android.inputmethod.latin",
            "com.google.android.googlequicksearchbox"
    ));

    // Codenames for currently supported Pixels by Google
    private static final ArrayList<String> pixelCodenames = 
    new ArrayList<String> (
        Arrays.asList(
            "comet",
            "komodo",
            "caiman",
            "tokay",
            "akita",
            "husky",
            "shiba",
            "felix",
            "tangorpro",
            "lynx",
            "cheetah",
            "panther",
            "bluejay",
            "oriole",
            "raven"
    ));

    static {
        propsToChangePixel = new HashMap<>();
        String fingerprint_pixel = "google/bluejay/bluejay:14/AP1A.240505.004/11583682:user/release-keys";
        propsToChangePixel.put("MANUFACTURER", "Google");
        propsToChangePixel.put("MODEL", "Pixel 6a");
        propsToChangePixel.put("FINGERPRINT", fingerprint_pixel);
        String[] fpsections_pixel = fingerprint_pixel.split("/");
        propsToChangePixel.put("BRAND", fpsections_pixel[0]);
        propsToChangePixel.put("DEVICE", fpsections_pixel[2].split(":")[0]);
        propsToChangePixel.put("PRODUCT", fpsections_pixel[1]);
        propsToChangePixel.put("ID", fpsections_pixel[3]);
        propsToSpoofPhotos = new HashMap<>();
        propsToSpoofPhotos.put("BRAND", "google");
        propsToSpoofPhotos.put("MANUFACTURER", "Google");
        propsToSpoofPhotos.put("DEVICE", "sailfish");
        propsToSpoofPhotos.put("PRODUCT", "sailfish");
        propsToSpoofPhotos.put("MODEL", "Pixel");
        propsToSpoofPhotos.put("ID", "QP1A.191005.007.A3");
        propsToSpoofPhotos.put("FINGERPRINT", "google/sailfish/sailfish:10/QP1A.191005.007.A3/5972272:user/release-keys");
    }

    private static void dlog(String message) {
        if (DEBUG) Log.d(TAG, message);
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (packageName == null || packageName.isEmpty()
            || !packageName.startsWith("com.google")) {
            return;
        }
        // Don't go through apps spoofing for supported pixels
        if (pixelCodenames.contains(DEVICE)) {
            return;
        }

        Map<String, Object> propsToChange = new HashMap<>();

        if (packagesToChangePixel.contains(packageName)
            || packagesToChangePixel.contains(processName)) {
                propsToChange.putAll(propsToChangePixel);
        } else if (packageName.equals(PACKAGE_PHOTOS)) {
            propsToChange.putAll(propsToSpoofPhotos);
        }

        if (propsToChange.isEmpty()){
            dlog("Nothing to define for: " + packageName);
        } else {
            dlog("Defining props for: " + packageName);
            for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                dlog("Defining " + key + " prop for: " + packageName);
                    setPropValue(key, value);
            }
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            dlog("Defining prop " + key + " to " + value.toString());
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            // Edit
            field.set(null, value);
            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setBuildField(String key, String value) {
        try {
            dlog("Defining build field " + key + " to " + value);
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionField(String key, Object value) {
        try {
            dlog("Defining version field " + key + " to " + value.toString());
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set version field " + key, e);
        }
    }
}
