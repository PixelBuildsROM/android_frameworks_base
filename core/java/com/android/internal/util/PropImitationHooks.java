/*
 * Copyright (C) 2022 Paranoid Android
 * Copyright (C) 2022 StatiXOS
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

package com.android.internal.util;

import android.app.Application;
import android.content.res.Resources;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = false;

    private static final String sCertifiedFp =
            Resources.getSystem().getString(R.string.config_certifiedFingerprint);

    private static final String sStockFp =
            Resources.getSystem().getString(R.string.config_stockFingerprint);

    private static final String ANDROIDX_TEST = "androidx.test";
    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS_RESTORE = "com.google.android.apps.restore";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = "unstable";
    private static final String PROCESS_GMS_PERSISTENT = "persistent";
    private static final String PROCESS_GMS_PIXEL_MIGRATE = "pixelmigrate";
    private static final String PROCESS_INSTRUMENTATION = "instrumentation";

    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_SUBSCRIPTION_RED = "com.google.android.apps.subscriptions.red";
    private static final String PACKAGE_TURBO = "com.google.android.apps.turbo";
    private static final String PACKAGE_VELVET = "com.google.android.googlequicksearchbox";
    private static final String PACKAGE_GBOARD = "com.google.android.inputmethod.latin";
    private static final String PACKAGE_SETUPWIZARD = "com.google.android.setupwizard";

    private static final Map<String, Object> sP7Props = createGoogleSpoofProps(
            "cheetah", "Pixel 7 Pro", "google/cheetah/cheetah:13/TQ3A.230705.001/10216780:user/release-keys");
    private static final Map<String, Object> gPhotosProps = createGoogleSpoofProps(
            "marlin", "Pixel XL", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");

    private static Map<String, Object> createGoogleSpoofProps(String device, String model, String fingerprint) {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "google");
        props.put("MANUFACTURER", "Google");
        props.put("DEVICE", device);
        props.put("PRODUCT", device);
        props.put("MODEL", model);
        props.put("FINGERPRINT", fingerprint);
        return props;
    }

    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;

    public static void setProps(Application app) {
        final String packageName = app.getPackageName();
        final String processName = app.getProcessName();

        if (packageName == null || processName == null) {
            return;
        }

        boolean isPackageGms = packageName.toLowerCase().contains(ANDROIDX_TEST) 
        	    || packageName.equals(PACKAGE_GMS_RESTORE) 
        	    || packageName.equals(PACKAGE_GMS);
        sIsGms = isPackageGms && processName.toLowerCase().contains(PROCESS_GMS_UNSTABLE) 
                || processName.toLowerCase().contains(PROCESS_GMS_PERSISTENT) 
                || processName.toLowerCase().contains(PROCESS_GMS_PIXEL_MIGRATE)
                || processName.toLowerCase().contains(PROCESS_INSTRUMENTATION);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);

        if (sIsGms) {
            dlog("Setting Pixel 2 fingerprint for: " + packageName);
            spoofBuildGms();
        } else if (!sCertifiedFp.isEmpty() && sIsFinsky) {
            dlog("Setting certified fingerprint for: " + packageName);
            setPropValue("FINGERPRINT", sCertifiedFp);
        } else if (!sStockFp.isEmpty() && packageName.equals(PACKAGE_ARCORE)) {
            dlog("Setting stock fingerprint for: " + packageName);
            setPropValue("FINGERPRINT", sStockFp);
        } else {
            switch (packageName) {
                case PACKAGE_SUBSCRIPTION_RED:
                case PACKAGE_TURBO:
                case PACKAGE_VELVET:
                case PACKAGE_GBOARD:
                case PACKAGE_SETUPWIZARD:
                case PACKAGE_GMS:
                    dlog("Spoofing Pixel 7 Pro for: " + packageName);
                    sP7Props.forEach((k, v) -> setPropValue(k, v));
                    break;
                case PACKAGE_GPHOTOS:
                    if (SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", true)) {
                        dlog("Spoofing as Pixel XL for: " + packageName);
                        gPhotosProps.forEach((k, v) -> setPropValue(k, v));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static void setPropValue(String key, Object value){
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setVersionField(String key, Integer value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            // Edit
            field.set(null, value);
            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void spoofBuildGms() {
        // Alter model name and fingerprint to Pixel 2 to avoid hardware attestation enforcement
        setPropValue("DEVICE", "walleye");
        setPropValue("FINGERPRINT", "google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys");
        setPropValue("MODEL", "Pixel 2");
        setPropValue("PRODUCT", "walleye");
        setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.O);
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if (isCallerSafetyNet() || sIsFinsky) {
            dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
      if (DEBUG) Log.d(TAG, msg);
    }
}
