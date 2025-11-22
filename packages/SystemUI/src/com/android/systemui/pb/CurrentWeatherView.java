/*
 * Copyright (C) 2023-2024 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.pb;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.internal.util.pb.OmniJawsClient;
import com.android.systemui.R;

public class CurrentWeatherView extends FrameLayout implements OmniJawsClient.OmniJawsObserver {

    private ImageView mCurrentImage;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private TextView mLeftText;
    private TextView mRightText;
    private Context mContext;
    private HandlerThread mWeatherThread;
    private Handler mBgHandler;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private SettingsObserver mSettingsObserver;

    private boolean mShowWeatherLocation;

    public CurrentWeatherView(Context context) {
        this(context, null);
    }

    public CurrentWeatherView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CurrentWeatherView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mWeatherClient = OmniJawsClient.get(); // singleton
    }

    private final Runnable mWeatherRunnable = new Runnable() {
        @Override
        public void run() {
            mWeatherClient.queryWeather(mContext);
            mWeatherInfo = mWeatherClient.getWeatherInfo();
            mMainHandler.post(mUpdateViewsRunnable);
        }
    };

    private final Runnable mUpdateViewsRunnable = new Runnable() {
        @Override
        public void run() {
            if (mWeatherInfo != null) {
                Drawable d = mWeatherClient.getWeatherConditionImage(mContext,
                        mWeatherInfo.conditionCode);
                mCurrentImage.setImageDrawable(d);
                mRightText.setText(mWeatherInfo.temp + " " + mWeatherInfo.tempUnits);
                mLeftText.setText(mWeatherInfo.city);
                mLeftText.setVisibility(mShowWeatherLocation ? View.VISIBLE : View.GONE);
            }
        }
    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCurrentImage  = findViewById(R.id.current_image);
        mLeftText = findViewById(R.id.left_text);
        mRightText = findViewById(R.id.right_text);
    }

    public void enableUpdates() {
        if (mBgHandler == null) {
            mWeatherThread = new HandlerThread("WeatherThread");
            mWeatherThread.start();
            mBgHandler = new Handler(mWeatherThread.getLooper());
        }
        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(mMainHandler);
            mSettingsObserver.observe();
        }
        if (mWeatherClient != null) {
            mWeatherClient.addObserver(mContext, this);
            mBgHandler.post(mWeatherRunnable);
        }
    }

    public void disableUpdates() {
        if (mSettingsObserver != null){
            mSettingsObserver.unobserve();
        }
        if (mWeatherClient != null) {
            mWeatherClient.removeObserver(mContext, this);
        }
        if (mWeatherThread != null) {
            if (mBgHandler != null) {
                mBgHandler.removeCallbacks(mWeatherRunnable);
                mBgHandler = null;
            }
            mWeatherThread.quitSafely();
            mWeatherThread = null;
        }
    }

    private void setErrorView() {
        mCurrentImage.setImageDrawable(null);
        mLeftText.setText("");
        mRightText.setText("");
    }

    @Override
    public void weatherError(int errorReason) {
        // since this is shown in ambient and lock screen
        // it would look bad to show every error since the
        // screen-on revovery of the service had no chance
        // to run fast enough
        // so only show the disabled state
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null;
            setErrorView();
        }
    }

    @Override
    public void weatherUpdated() {
        mBgHandler.post(mWeatherRunnable);
    }

    @Override
    public void updateSettings() {
        mBgHandler.post(mWeatherRunnable);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.LOCKSCREEN_WEATHER_LOCATION),
                    false, this, UserHandle.USER_ALL
            );
            updateWeatherSettings();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        void updateWeatherSettings() {
            mShowWeatherLocation = Settings.System.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_WEATHER_LOCATION,
                    0, UserHandle.USER_CURRENT) != 0;
            mLeftText.setVisibility(mShowWeatherLocation ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateWeatherSettings();
        }
    }
}
