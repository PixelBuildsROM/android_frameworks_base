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

import android.content.Context;
import android.os.SystemProperties;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();

    private static final String PACKAGE_PHOTOS = "com.google.android.apps.photos";

    private static final boolean DEBUG = SystemProperties.getBoolean("ro.debug.pixelpropsutils", false);

    private static final Map<String, Object> propsToSpoofPhotos;

    static {
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

        if (packageName.equals("com.google.android.apps.photos")) {
            Map<String, Object> propsToChange = new HashMap<>();

            propsToChange.putAll(propsToSpoofPhotos);

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
}
