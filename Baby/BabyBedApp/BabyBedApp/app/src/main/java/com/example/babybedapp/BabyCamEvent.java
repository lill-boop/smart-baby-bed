package com.example.babybedapp;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * BabyCam Event Data Model
 * Represents a single event from events.jsonl
 */
public class BabyCamEvent {
    private long ts; // Unix timestamp
    private String type; // Event type (cry, motion, etc.)
    private int seconds; // Video duration
    private String file; // Filename
    private String url; // Full URL to video
    private String createdAt; // ISO format datetime

    public BabyCamEvent() {
    }

    /**
     * Parse from JSONL line
     */
    public static BabyCamEvent fromJson(String jsonLine) throws JSONException {
        JSONObject json = new JSONObject(jsonLine);
        BabyCamEvent event = new BabyCamEvent();

        event.ts = json.optLong("ts", 0);
        event.type = json.optString("type", "unknown");
        event.seconds = json.optInt("seconds", 60);
        event.file = json.optString("file", "");
        event.url = json.optString("url", "");
        event.createdAt = json.optString("created_at", "");

        return event;
    }

    // Getters
    public long getTs() {
        return ts;
    }

    public String getType() {
        return type;
    }

    public int getSeconds() {
        return seconds;
    }

    public String getFile() {
        return file;
    }

    public String getUrl() {
        return url;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * Get formatted time string
     */
    public String getFormattedTime() {
        if (createdAt != null && !createdAt.isEmpty()) {
            try {
                String[] parts = createdAt.split("T");
                if (parts.length == 2) {
                    String date = parts[0];
                    String time = parts[1].split("\\.")[0];
                    return date + " " + time;
                }
            } catch (Exception e) {
                // Fall through to timestamp
            }
        }

        if (ts > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            return sdf.format(new java.util.Date(ts * 1000));
        }

        return "Unknown";
    }

    /**
     * Check if this is a cry event
     */
    public boolean isCryEvent() {
        return "cry".equalsIgnoreCase(type);
    }
}
