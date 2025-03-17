/*
  Copyright (C) 2024 The LeafOS Project
  Copyright (C) 2025 PixelBuildsROM

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package com.android.server.pb;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.app.ActivityManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.PowerManager;
import android.util.Log;
import android.util.Patterns;

import com.android.server.SystemService;
import com.android.internal.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class DynamicCertificationService extends SystemService {

    private static final String TAG = DynamicCertificationService.class.getSimpleName();
    private static final String DynamicCertificationAPI = Resources.getSystem().getString(
        R.string.dynamic_certification_URI);

    private static final String CERT_DATA_FILE = "certified_props.json";

    private static final long INITIAL_DELAY = 0;
    private static final long INTERVAL = 5;

    private static final boolean DEBUG = SystemProperties.getBoolean("ro.debug.dynamic_cert_service", false);

    private final Context mContext;
    private final File mDataFile;
    private final ScheduledExecutorService mScheduler;

    public DynamicCertificationService(Context context) {
        super(context);
        mContext = context;
        mDataFile = new File(Environment.getDataSystemDirectory(), CERT_DATA_FILE);
        mScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void onStart() {}

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            // Does that look like a URL?
            if (Patterns.WEB_URL.matcher(DynamicCertificationAPI).matches()) {
                Log.i(TAG, "Scheduling the service");
                mScheduler.scheduleAtFixedRate(
                    new FetchGmsCertifiedProps(), INITIAL_DELAY, INTERVAL, TimeUnit.MINUTES); 
            } 
        }
    }

    private String readFromFile(File file) {
        StringBuilder content = new StringBuilder();

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from file", e);
            }
        }
        return content.toString();
    }

    private void writeToFile(File file, String data) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(data);
            // Set -rw-r--r-- (644) permission to make it readable by others.
            file.setReadable(true, false);
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file", e);
        }
    }

    private static void killGms(Context context) {
        final ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        activityManager.forceStopPackage("com.google.android.gms");
    }

    private String fetchProps() {
        try {
            URL url = new URL(DynamicCertificationAPI);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            try {
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                connection.connect();

                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    return reader.lines().collect(Collectors.joining());
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error making an API request", e);
            return null;
        }
    }

    private boolean isInternetConnected() {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        Network nw = cm.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = cm.getNetworkCapabilities(nw);
        return actNw != null
                && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }
    
    private boolean isInteractive() {
        PowerManager pwm = mContext.getSystemService(PowerManager.class);
        return pwm.isInteractive();
    }

    private void dlog(String message) {
        if (DEBUG) Log.d(TAG, message);
    }

    private class FetchGmsCertifiedProps implements Runnable {
        @Override
        public void run() {
            try {
                dlog("FetchGmsCertifiedProps started");

                if (!isInternetConnected() || !isInteractive()) {
                    dlog("Internet unavailable or device is idle");
                    return;
                }

                String savedProps = readFromFile(mDataFile);
                String fetchedProps = fetchProps();

                if (fetchedProps != null && !savedProps.equals(fetchedProps)) {
                    dlog("Found new props");
                    writeToFile(mDataFile, fetchedProps);
                    dlog("FetchGmsCertifiedProps completed");
                    killGms(mContext);
                    dlog("Gms process was stopped");
                } else {
                    dlog("No change in props");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in FetchGmsCertifiedProps", e);
            }
        }
    }
}
