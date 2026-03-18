package com.example.babybedapp;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * MQTT Status JSON 解析器
 * 支持新字段 (temp_x10, temp_alarm, wet, cry) 和旧字段 (beep_temp, beep_humi, voice) 兼容
 */
public class StatusParser {
    private static final String TAG = "StatusParser";

    /**
     * 解析 MQTT status JSON 消息
     * 
     * @param rawJson 原始 JSON 字符串
     * @return DeviceStatus 对象，解析失败返回 null
     */
    public static DeviceStatus parse(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            Log.w(TAG, "RX: [empty message]");
            return null;
        }

        Log.d(TAG, "RX 原文: " + rawJson);

        try {
            JSONObject json = new JSONObject(rawJson);
            DeviceStatus status = new DeviceStatus();

            // ===== 温度解析 =====
            // 优先使用 temp_x10 (新字段, 整数*10)
            if (json.has("temp_x10")) {
                int tempX10 = getIntSafe(json, "temp_x10", 0);
                status.tempC = tempX10 / 10.0;
            }

            // ===== 温度报警 =====
            // 优先使用 temp_alarm, 降级到 beep_temp
            if (json.has("temp_alarm")) {
                status.tempAlarm = getIntSafe(json, "temp_alarm", 0);
            } else if (json.has("beep_temp")) {
                status.tempAlarm = getIntSafe(json, "beep_temp", 0);
            }

            // ===== 尿床报警 =====
            // 优先使用 wet, 降级到 beep_humi
            if (json.has("wet")) {
                status.wetAlarm = getIntSafe(json, "wet", 0);
            } else if (json.has("beep_humi")) {
                status.wetAlarm = getIntSafe(json, "beep_humi", 0);
            }

            // ===== 哭声报警 =====
            // 优先使用 cry, 降级到 voice 推断
            // cry==0 或 voice==0 表示检测到哭声(报警)，==1 表示正常
            if (json.has("cry")) {
                int cry = getIntSafe(json, "cry", 1);
                status.cryAlarm = (cry == 0) ? 1 : 0;
            } else if (json.has("voice")) {
                int voice = getIntSafe(json, "voice", 1);
                status.cryAlarm = (voice == 0) ? 1 : 0;
            }

            // ===== 控制状态 =====
            status.mode = getIntSafe(json, "mode", 0);
            status.fanFlag = getIntSafe(json, "fan_flag", 0);
            status.hotFlag = getIntSafe(json, "hot_flag", 0);
            status.cribFlag = getIntSafe(json, "crib_flag", 0);
            status.cribFlag = getIntSafe(json, "crib_flag", 0);
            status.musicFlag = getIntSafe(json, "music_flag", 0);

            // alert_15s 可能是 boolean (旧) 或 string (新)
            status.alert15sMsg = "";
            if (json.has("alert_15s")) {
                Object val = json.get("alert_15s");
                if (val instanceof String) {
                    status.alert15sMsg = (String) val;
                } else if (val instanceof Boolean && (Boolean) val) {
                    status.alert15sMsg = "检测到危险"; // Fallback
                }
            }

            Log.d(TAG, "解析结果: " + status.toString());
            return status;

        } catch (JSONException e) {
            Log.e(TAG, "JSON 解析错误: " + e.getMessage() + ", 原文: " + rawJson);
            return null;
        }
    }

    /**
     * 安全获取 int 值，处理类型异常
     */
    private static int getIntSafe(JSONObject json, String key, int defaultValue) {
        try {
            if (!json.has(key))
                return defaultValue;

            Object value = json.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            } else if (value instanceof Boolean) {
                return ((Boolean) value) ? 1 : 0;
            }
        } catch (Exception e) {
            Log.w(TAG, "字段 '" + key + "' 类型转换异常: " + e.getMessage());
        }
        return defaultValue;
    }
}
