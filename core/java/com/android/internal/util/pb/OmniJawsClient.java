/*
* Copyright (C) 2021 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.internal.util.pb;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Iterator;
import java.lang.ref.WeakReference;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

public class OmniJawsClient {
    private static final String TAG = OmniJawsClient.class.getSimpleName();
    private static final boolean DEBUG = false;
    public static final String SERVICE_PACKAGE = "org.omnirom.omnijaws";
    public static final Uri WEATHER_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/weather");
    public static final Uri SETTINGS_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/settings");
    public static final Uri CONTROL_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/control");

    private static final String ICON_PACKAGE_DEFAULT = "org.omnirom.omnijaws";
    private static final String ICON_PREFIX_DEFAULT = "google_new_light";
    private static final String ICON_PREFIX_OUTLINE = "outline";
    private static final String EXTRA_ERROR = "error";
    public static final int EXTRA_ERROR_NETWORK = 0;
    public static final int EXTRA_ERROR_LOCATION = 1;
    public static final int EXTRA_ERROR_DISABLED = 2;

    public static final String[] WEATHER_PROJECTION = new String[]{
            "city",
            "wind_speed",
            "wind_direction",
            "condition_code",
            "temperature",
            "humidity",
            "condition",
            "forecast_low",
            "forecast_high",
            "forecast_condition",
            "forecast_condition_code",
            "time_stamp",
            "forecast_date",
            "pin_wheel"
    };

    public static final String[] SETTINGS_PROJECTION = new String[] {
            "enabled",
            "units",
            "provider",
            "setup",
            "icon_pack"
    };

    private static final String WEATHER_UPDATE = SERVICE_PACKAGE + ".WEATHER_UPDATE";
    private static final String WEATHER_ERROR = SERVICE_PACKAGE + ".WEATHER_ERROR";

    private static final DecimalFormat sNoDigitsFormat = new DecimalFormat("0");

    public static class WeatherInfo {
        public String city;
        public String windSpeed;
        public String windDirection;
        public int conditionCode;
        public String temp;
        public String humidity;
        public String condition;
        public Long timeStamp;
        public List<DayForecast> forecasts;
        public String tempUnits;
        public String windUnits;
        public String provider;
        public String pinWheel;
        public String iconPack;

        public String toString() {
            return city + ":" + new Date(timeStamp) + ": " + windSpeed + ":" + windDirection + ":" +conditionCode + ":" + temp + ":" + humidity + ":" + condition + ":" + tempUnits + ":" + windUnits + ": " + forecasts + ": " + iconPack;
        }

        public String getLastUpdateTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return sdf.format(new Date(timeStamp));
        }
    }

    public static class DayForecast {
        public String low;
        public String high;
        public int conditionCode;
        public String condition;
        public String date;

        public String toString() {
            return "[" + low + ":" + high + ":" +conditionCode + ":" + condition + ":" + date + "]";
        }
    }

    public static interface OmniJawsObserver {
        public void weatherUpdated();
        public void weatherError(int errorReason);
        default public void updateSettings() {};
    }

    private class WeatherUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Prune dead observers
            try {
                mObservers.removeIf(ref -> ref.get() == null);
            } catch (Exception e) {
                Log.w(TAG, "Exception occured while pruning, ignoring");
            }

            for (WeakReference<OmniJawsObserver> ref : mObservers) {
                OmniJawsObserver obs = ref.get();
                if (obs == null) continue;
                if (WEATHER_UPDATE.equals(action)) {
                    obs.weatherUpdated();
                } else if (WEATHER_ERROR.equals(action)) {
                    obs.weatherError(intent.getIntExtra(EXTRA_ERROR, 0));
                }
            }
        }
    }

    private static OmniJawsClient sInstance;

    private WeatherInfo mCachedInfo;
    private Resources mRes;
    private String mPackageName;
    private String mIconPrefix;
    private String mSettingIconPackage;
    private boolean mMetric;
    private final List<WeakReference<OmniJawsObserver>> mObservers = new ArrayList<>();
    private WeatherUpdateReceiver mReceiver;
    private boolean mWeatherReceiverRegistered = false;

    public static OmniJawsClient get() {
        if (sInstance == null) {
            synchronized (OmniJawsClient.class) {
                if (sInstance == null) {
                    sInstance = new OmniJawsClient();
                }
            }
        }
        return sInstance;
    }
    
    public Intent getSettingsIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".SettingsActivity");
    }

    public static Intent getWeatherActivityIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".WeatherActivity")
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public WeatherInfo getWeatherInfo() {
        return mCachedInfo;
    }

    private static String getFormattedValue(float value) {
        if (Float.isNaN(value)) {
            return "-";
        }
        String formatted = sNoDigitsFormat.format(value);
        if (formatted.equals("-0")) {
            formatted = "0";
        }
        return formatted;
    }

    public void queryWeather(Context context) {
        if (!isOmniJawsEnabled(context)) {
            Log.w(TAG, "queryWeather while disabled");
            mCachedInfo = null;
            return;
        }

        try (Cursor wc = context.getContentResolver().query(WEATHER_URI, WEATHER_PROJECTION,
                    null, null, null)) {
            mCachedInfo = null;
            int count = wc.getCount();
            if (wc != null && count > 0) {
                mCachedInfo = new WeatherInfo();
                List<DayForecast> forecastList = new ArrayList<DayForecast>();
                int i = 0;
                for (i = 0; i < count; i++) {
                    wc.moveToPosition(i);
                    if (i == 0) {
                        mCachedInfo.city = wc.getString(0);
                        mCachedInfo.windSpeed = getFormattedValue(wc.getFloat(1));
                        mCachedInfo.windDirection = String.valueOf(wc.getInt(2)) + "\u00b0";
                        mCachedInfo.conditionCode = wc.getInt(3);
                        mCachedInfo.temp = getFormattedValue(wc.getFloat(4));
                        mCachedInfo.humidity = wc.getString(5);
                        mCachedInfo.condition = wc.getString(6);
                        mCachedInfo.timeStamp = Long.parseLong(wc.getString(11));
                        mCachedInfo.pinWheel = wc.getString(13);
                    } else {
                        DayForecast day = new DayForecast();
                        day.low = getFormattedValue(wc.getFloat(7));
                        day.high = getFormattedValue(wc.getFloat(8));
                        day.condition = wc.getString(9);
                        day.conditionCode = wc.getInt(10);
                        day.date = wc.getString(12);
                        forecastList.add(day);
                    }
                }
                mCachedInfo.forecasts = forecastList;
            }
        } catch (Exception e) {
            Log.e(TAG, "queryWeather: failed to query weather", e);
        }

        try (Cursor sc = context.getContentResolver().query(
                SETTINGS_URI, SETTINGS_PROJECTION, null, null, null)) {
            if (sc != null && sc.getCount() > 0) {
                sc.moveToPosition(0);
                mMetric = sc.getInt(1) == 0;
                if (mCachedInfo != null) {
                    mCachedInfo.tempUnits = getTemperatureUnit();
                    mCachedInfo.windUnits = getWindUnit();
                    mCachedInfo.provider = sc.getString(2);
                    mCachedInfo.iconPack = sc.getString(4);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "queryWeather: failed to get settings", e);
        }

        updateSettings(context);
    }

    private void loadDefaultIconsPackage(Context context) {
        mPackageName = ICON_PACKAGE_DEFAULT;
        mIconPrefix = ICON_PREFIX_DEFAULT;
        mSettingIconPackage = mPackageName + "." + mIconPrefix;
        if (DEBUG) Log.d(TAG, "Load default icon pack " + mSettingIconPackage + " " + mPackageName + " " + mIconPrefix);
        try {
            PackageManager packageManager = context.getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
        } catch (Exception e) {
            mRes = null;
        }
        if (mRes == null) {
            Log.w(TAG, "No default package found");
        }
    }

    private Drawable getDefaultConditionImage(Context context) {
        String packageName = ICON_PACKAGE_DEFAULT;
        String iconPrefix = ICON_PREFIX_DEFAULT;

        try {
            PackageManager packageManager = context.getPackageManager();
            Resources res = packageManager.getResourcesForApplication(packageName);
            if (res != null) {
                int resId = res.getIdentifier(iconPrefix + "_na", "drawable", packageName);
                Drawable d = res.getDrawable(resId, null);
                if (d != null) {
                    return d;
                }
            }
        } catch (Exception e) {
        }
        // absolute absolute fallback
        Log.w(TAG, "No default package found");
        return new ColorDrawable(Color.RED);
    }

    private void loadCustomIconPackage(Context context) {
        if (DEBUG) Log.d(TAG, "Load custom icon pack " + mSettingIconPackage);
        int idx = mSettingIconPackage.lastIndexOf(".");
        mPackageName = mSettingIconPackage.substring(0, idx);
        mIconPrefix = mSettingIconPackage.substring(idx + 1);
        if (DEBUG) Log.d(TAG, "Load custom icon pack " + mPackageName + " " + mIconPrefix);
        try {
            PackageManager packageManager = context.getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
        } catch (Exception e) {
            mRes = null;
        }
        if (mRes == null) {
            Log.w(TAG, "Icon pack loading failed - loading default");
            loadDefaultIconsPackage(context);
        }
    }

    public Drawable getWeatherConditionImage(Context context, int conditionCode) {
        try {
            int resId = mRes.getIdentifier(mIconPrefix + "_" + conditionCode, "drawable", mPackageName);
            Drawable d = mRes.getDrawable(resId, null);
            if (d != null) {
                return d;
            }
            Log.w(TAG, "Failed to get condition image for " + conditionCode + " use default");
            resId = mRes.getIdentifier(mIconPrefix + "_na", "drawable", mPackageName);
            d = mRes.getDrawable(resId, null);
            if (d != null) {
                return d;
            }
        } catch(Exception e) {
            Log.e(TAG, "getWeatherConditionImage", e);
        }
        Log.w(TAG, "Failed to get condition image for " + conditionCode);
        return getDefaultConditionImage(context);
    }

    public boolean isOmniJawsEnabled(Context context) {
        if (!isServiceAvailable(context)) return false;

        try (Cursor c = context.getContentResolver().query(
                SETTINGS_URI, SETTINGS_PROJECTION, null, null, null)) {
            return c != null && c.moveToFirst() && c.getInt(0) == 1;
        } catch (Exception e) {
            Log.e(TAG, "isOmniJawsEnabled:", e);
            return false;
        }
    }

    private boolean isServiceAvailable(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            int state = pm.getApplicationEnabledSetting(SERVICE_PACKAGE);
            return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String getTemperatureUnit() {
        return "\u00b0" + (mMetric ? "C" : "F");
    }

    private String getWindUnit() {
        return mMetric ? "km/h":"mph";
    }

    private void updateSettings(Context context) {
        final String iconPack = mCachedInfo != null ? mCachedInfo.iconPack : null;
        if (TextUtils.isEmpty(iconPack)) {
            loadDefaultIconsPackage(context);
        } else if (mSettingIconPackage == null || !iconPack.equals(mSettingIconPackage)) {
            mSettingIconPackage = iconPack;
            loadCustomIconPackage(context);
        }
    }

    public void addObserver(Context context, OmniJawsObserver observer) {
        if (observer == null) return;
        removeObserver(context, observer);
        mObservers.add(new WeakReference<>(observer));
        registerReceiverIfNeeded(context);
    }

    public void removeObserver(Context context, OmniJawsObserver observer) {
        if (observer == null) return;
        Iterator<WeakReference<OmniJawsObserver>> it = mObservers.iterator();
        while (it.hasNext()) {
            OmniJawsObserver o = it.next().get();
            if (o == null || o == observer) {
                it.remove();
            }
        }
        if (mObservers.isEmpty()) {
            unregisterReceiver(context);
        }
    }

    private void registerReceiverIfNeeded(Context context) {
        if (!mWeatherReceiverRegistered && !mObservers.isEmpty()) {
            if (mReceiver != null) {
                unregisterReceiver(context);
            }
            mReceiver = new WeatherUpdateReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(WEATHER_UPDATE);
            filter.addAction(WEATHER_ERROR);
            context.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
            mWeatherReceiverRegistered = true;
        }
    }

    private void unregisterReceiver(Context context) {
        if (mWeatherReceiverRegistered && mReceiver != null) {
            try {
                context.unregisterReceiver(mReceiver);
            } catch (Exception ignored) {}
            mWeatherReceiverRegistered = false;
        }
    }

    public boolean isOutlineIconPackage() {
        return mIconPrefix.equals(ICON_PREFIX_OUTLINE);
    }
}
