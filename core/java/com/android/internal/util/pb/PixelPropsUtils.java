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

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.os.Environment;
import android.util.Log;
import android.text.TextUtils;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final String DEVICE = SystemProperties.get("ro.build.version.device");
    private static final String MODEL = SystemProperties.get("ro.product.model", Build.MODEL);


    private static final String CERT_DATA_FILE = "certified_props.json";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_PHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_SET_INTEL = "com.google.android.settings.intelligence";
    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

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

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    private static String[] dynamicProps() {
        File dataFile = new File(Environment.getDataSystemDirectory(), CERT_DATA_FILE);

        if (!dataFile.exists()) {
            Log.w(TAG, "File not found: " + dataFile.getAbsolutePath() + 
                " using overlayed props for Gms");
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {

            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            if (!TextUtils.isEmpty(jsonContent)){
                JSONArray jsonArray = new JSONArray(jsonContent.toString());
                String[] result = new String[jsonArray.length()];

                for (int i = 0; i < jsonArray.length(); i++) {
                    result[i] = jsonArray.getString(i);
                }

                return result;
            } else {
                Log.w(TAG, "Dynamic props JSON has no data, using overlayed props for Gms");
                return null;
            }

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Exception while parsing dynamic props JSON file", e);
            return null;
        }
    }
    
    private static void setPropsForGms() {
        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was + ", killing myself!");
                    // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
        if (was) return;

        // Give up if not appropriate props array
        if (sCertifiedProps.length != 5) {
            Log.e(TAG, "Insufficient size of the certified props array: "
                    + sCertifiedProps.length + ", required 5");
            return;
        } else {
            dlog("Spoofing build for GMS");
            setBuildField("MANUFACTURER", sCertifiedProps[0]);
            setBuildField("MODEL", sCertifiedProps[1]);
            setVersionField("SECURITY_PATCH", sCertifiedProps[2]);
            setVersionField("DEVICE_INITIAL_SDK_INT", Integer.parseInt(sCertifiedProps[3]));
            setBuildField("FINGERPRINT", sCertifiedProps[4]);
            String[] certfpsections = sCertifiedProps[4].split("/");
            setBuildField("BRAND", certfpsections[0]);
            setBuildField("DEVICE", certfpsections[2].split(":")[0]);
            setBuildField("PRODUCT", certfpsections[1]);
            setBuildField("ID", certfpsections[3]);
            setVersionField("RELEASE", certfpsections[2].split(":")[1]);
            setVersionField("INCREMENTAL", certfpsections[4].split(":")[0]);
        }
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (packageName == null || packageName.isEmpty()
            || !packageName.startsWith("com.google")) {
            return;
        }
        // Detect and spoof GMS first
        if (packageName.equals(PACKAGE_GMS)) {
            setPropValue("TIME", System.currentTimeMillis());
            if (processName.toLowerCase().contains("unstable")) {
                String[] sDynamicProps = dynamicProps();
                if (sDynamicProps != null && !Arrays.equals(sCertifiedProps, sDynamicProps)) {
                    sCertifiedProps = sDynamicProps;
                }
                setPropsForGms();
                return;
            }
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

        // Set proper indexing fingerprint
        if (packageName.equals(PACKAGE_SET_INTEL)) {
            setPropValue("FINGERPRINT", Build.VERSION.INCREMENTAL);
        }
        // Show correct model name on gms services
        if ("com.google.android.gms.ui".equals(processName)) {
            setPropValue("MODEL", MODEL);
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
