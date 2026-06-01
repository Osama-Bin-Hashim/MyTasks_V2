package com.example.mytasks;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Global Configuration for Backend and Networking
 */
public class AppConfig {
    // TOGGLE: Set to true when the local Flask server is running
    public static final boolean USE_SERVER_BACKEND = true;

    public static final String DEFAULT_IP = "10.156.153.10";

    /**
     * Retrieves the dynamic Base URL from SharedPreferences
     */
    public static String getBaseUrl(Context context) {
        SharedPreferences pref = context.getSharedPreferences("NetworkConfig", Context.MODE_PRIVATE);
        String savedIp = pref.getString("SERVER_IP", DEFAULT_IP);
        if (savedIp.isEmpty()) savedIp = DEFAULT_IP;
        return "http://" + savedIp + ":5000/";
    }
}
