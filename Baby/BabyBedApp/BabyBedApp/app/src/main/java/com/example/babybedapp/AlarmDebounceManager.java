package com.example.babybedapp;

import android.util.Log;

/**
 * 报警防抖管理器
 * 管理三种报警（温度/尿床/哭声）的状态机和去重逻辑
 * 
 * 规则：
 * 1. 报警从 0→1 时弹窗
 * 2. 同一种报警 60 秒内只弹一次
 * 3. 报警持续为 1 时不重复弹
 * 4. 报警恢复为 0 后，下次再变 1 可以再弹
 */
public class AlarmDebounceManager {
    private static final String TAG = "AlarmDebounceManager";
    private static final long DEBOUNCE_WINDOW_MS = 60 * 1000; // 60秒

    public enum AlarmType {
        TEMP, WET, CRY
    }

    // 上次报警值
    private int lastTempAlarm = 0;
    private int lastWetAlarm = 0;
    private int lastCryAlarm = 0;

    // 上次弹窗时间
    private long lastTempPopupTime = 0;
    private long lastWetPopupTime = 0;
    private long lastCryPopupTime = 0;

    /**
     * 检查是否应该显示弹窗
     * 
     * @param type     报警类型
     * @param newValue 新的报警值
     * @return true 表示应该弹窗
     */
    public boolean shouldShowPopup(AlarmType type, int newValue) {
        long now = System.currentTimeMillis();
        boolean shouldShow = false;

        switch (type) {
            case TEMP:
                // 检查 0→1 转换
                if (lastTempAlarm == 0 && newValue == 1) {
                    // 检查 60 秒去重
                    if (now - lastTempPopupTime > DEBOUNCE_WINDOW_MS) {
                        shouldShow = true;
                        lastTempPopupTime = now;
                        Log.d(TAG, "温度报警触发弹窗");
                    } else {
                        Log.d(TAG, "温度报警在60秒内已弹过，跳过");
                    }
                }
                lastTempAlarm = newValue;
                break;

            case WET:
                if (lastWetAlarm == 0 && newValue == 1) {
                    if (now - lastWetPopupTime > DEBOUNCE_WINDOW_MS) {
                        shouldShow = true;
                        lastWetPopupTime = now;
                        Log.d(TAG, "尿床报警触发弹窗");
                    } else {
                        Log.d(TAG, "尿床报警在60秒内已弹过，跳过");
                    }
                }
                lastWetAlarm = newValue;
                break;

            case CRY:
                if (lastCryAlarm == 0 && newValue == 1) {
                    if (now - lastCryPopupTime > DEBOUNCE_WINDOW_MS) {
                        shouldShow = true;
                        lastCryPopupTime = now;
                        Log.d(TAG, "哭声报警触发弹窗");
                    } else {
                        Log.d(TAG, "哭声报警在60秒内已弹过，跳过");
                    }
                }
                lastCryAlarm = newValue;
                break;
        }

        return shouldShow;
    }

    /**
     * 重置所有状态（可用于重新连接时）
     */
    public void reset() {
        lastTempAlarm = 0;
        lastWetAlarm = 0;
        lastCryAlarm = 0;
        lastTempPopupTime = 0;
        lastWetPopupTime = 0;
        lastCryPopupTime = 0;
        Log.d(TAG, "报警状态已重置");
    }
}
