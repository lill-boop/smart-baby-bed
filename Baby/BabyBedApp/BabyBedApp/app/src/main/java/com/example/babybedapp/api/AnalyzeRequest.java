package com.example.babybedapp.api;

import com.google.gson.annotations.SerializedName;

/**
 * 分析请求模型
 */
public class AnalyzeRequest {
    @SerializedName("device_id")
    public String deviceId;

    @SerializedName("range_minutes")
    public int rangeMinutes;

    @SerializedName("avg_temp")
    public double avgTemp;

    @SerializedName("max_temp")
    public double maxTemp;

    @SerializedName("wet_count")
    public int wetCount;

    @SerializedName("cry_count")
    public int cryCount;

    @SerializedName("temp_alarm_count")
    public int tempAlarmCount;

    @SerializedName("last_cry_minutes_ago")
    public int lastCryMinutesAgo;

    public AnalyzeRequest() {
    }

    public static AnalyzeRequest fromStats(String deviceId,
            double avgTemp, double maxTemp,
            int wetCount, int cryCount, int tempAlarmCount,
            int lastCryMinutesAgo) {
        AnalyzeRequest req = new AnalyzeRequest();
        req.deviceId = deviceId;
        req.rangeMinutes = 60;
        req.avgTemp = avgTemp;
        req.maxTemp = maxTemp;
        req.wetCount = wetCount;
        req.cryCount = cryCount;
        req.tempAlarmCount = tempAlarmCount;
        req.lastCryMinutesAgo = lastCryMinutesAgo;
        return req;
    }

    @Override
    public String toString() {
        return String.format(
                "AnalyzeRequest{device=%s, range=%dm, avgTemp=%.1f, maxTemp=%.1f, wet=%d, cry=%d, tempAlarm=%d, lastCry=%dm}",
                deviceId, rangeMinutes, avgTemp, maxTemp, wetCount, cryCount, tempAlarmCount, lastCryMinutesAgo);
    }
}
