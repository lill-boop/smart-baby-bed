package com.example.babybedapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SOC Configuration Manager
 * Manages SOC IP address and base URL for HTTP requests
 */
public class SocConfig {
    private static final String PREFS_NAME = "babycam_prefs";
    private static final String KEY_SOC_IP = "soc_ip";
    private static final String DEFAULT_SOC_IP = "192.168.0.222"; // LAN IP for video streaming
    private static final int HTTP_PORT = 8099;

    private final SharedPreferences prefs;

    public SocConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getSocIp() {
        return prefs.getString(KEY_SOC_IP, DEFAULT_SOC_IP);
    }

    public void setSocIp(String ip) {
        prefs.edit().putString(KEY_SOC_IP, ip).apply();
    }

    public String getBaseUrl() {
        return "http://" + getSocIp() + ":" + HTTP_PORT;
    }

    public String getClipsUrl() {
        return getBaseUrl() + "/clips/";
    }

    public String getEventsUrl() {
        return getBaseUrl() + "/events/events.jsonl";
    }

    public String getRingUrl() {
        return getBaseUrl() + "/ring/";
    }
}
