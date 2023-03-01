package com.android.internal.util.pb;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class PixelPropsDownloadHelper {

    private static final String TAG = PixelPropsDownloadHelper.class.getSimpleName();

    public static String[] downloadAndExtractArray(String[] creds) {
        try {
            // Create URL object
            URL url = new URL(creds[0]);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request method
            connection.setRequestMethod("GET");

            // Set token authentication header
            connection.setRequestProperty("Authorization", "token " + creds[1]);

            // Check response code
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read the response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine).append("\n");
                }

                // Close the streams
                in.close();
                connection.disconnect();

                // Parse the content and extract the string array
                if (content.length() > 0) {
                    return parseStringArray(content.toString());
                }
            } else {
                Log.e(TAG, "Failed to fetch, HTTP code " + responseCode);
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "Failed to fetch, invalid URL ", e);
        } catch (UnknownHostException e) {
            Log.w(TAG, "Failed to fetch, no internet connection? ", e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch, I/O exception! ", e);
        }
        // Return empty array on failure
        return new String[0];
    }

    private static String[] parseStringArray(String xmlContent) {
        List<String> list = new ArrayList<>();
        String[] lines = xmlContent.split("\n");
        boolean isPropsArray = false;

        for (String line : lines) {
            line = line.trim();
            if (line.contains("<string-array name=\"config_certifiedBuildProperties\"")) {
                isPropsArray = true;
            } else if (line.contains("</string-array>")) {
                isPropsArray = false;
            }

            if (isPropsArray && line.contains("<item>")) {
                int start = line.indexOf(">") + 1;
                int end = line.lastIndexOf("<");
                String item = line.substring(start, end).replace("\"", "");
                list.add(item);
            }
        }

        return list.toArray(new String[0]);
    }
}
