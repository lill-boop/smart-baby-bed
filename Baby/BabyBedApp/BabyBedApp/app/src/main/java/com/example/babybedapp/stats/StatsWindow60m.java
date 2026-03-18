package com.example.babybedapp.stats;

import android.util.Log;

import com.example.babybedapp.DeviceStatus;

import java.util.LinkedList;

/**
 * 60分钟滑动窗口统计器
 * 在内存中维护最近60分钟的统计数据
 */
public class StatsWindow60m {
    private static final String TAG = "StatsWindow60m";
    private static final long WINDOW_MS = 60 * 60 * 1000; // 60分钟
    private static final long EDGE_DEBOUNCE_MS = 60 * 1000; // 60秒边沿去抖

    // 样本列表（用于温度平均）
    private final LinkedList<TempSample> tempSamples = new LinkedList<>();

    // 边沿计数器
    private final EdgeCounter wetCounter = new EdgeCounter(EDGE_DEBOUNCE_MS);
    private final EdgeCounter cryCounter = new EdgeCounter(EDGE_DEBOUNCE_MS);
    private final EdgeCounter tempAlarmCounter = new EdgeCounter(EDGE_DEBOUNCE_MS);

    // 最后哭声时间
    private long lastCryTime = 0;

    // 当前统计结果
    private double avgTemp = 0;
    private double maxTemp = 0;
    private int wetCount = 0;
    private int cryCount = 0;
    private int tempAlarmCount = 0;

    /**
     * 处理新的状态数据
     * 
     * @return true 如果有任何边沿事件发生
     */
    public synchronized boolean onStatus(DeviceStatus status) {
        long now = status.timestamp;
        boolean hasEvent = false;

        // 添加温度样本
        if (status.tempC > 0) {
            tempSamples.add(new TempSample(now, status.tempC));
        }

        // 清理过期样本
        long cutoff = now - WINDOW_MS;
        while (!tempSamples.isEmpty() && tempSamples.peek().timestamp < cutoff) {
            tempSamples.poll();
        }

        // 计算温度统计
        if (!tempSamples.isEmpty()) {
            double sum = 0;
            maxTemp = 0;
            for (TempSample s : tempSamples) {
                sum += s.temp;
                if (s.temp > maxTemp) {
                    maxTemp = s.temp;
                }
            }
            avgTemp = sum / tempSamples.size();
        }

        // 边沿检测
        if (wetCounter.onValue(status.wetAlarm, now)) {
            wetCount++;
            hasEvent = true;
            Log.d(TAG, "检测到尿床事件, 累计: " + wetCount);
        }

        if (cryCounter.onValue(status.cryAlarm, now)) {
            cryCount++;
            lastCryTime = now;
            hasEvent = true;
            Log.d(TAG, "检测到哭声事件, 累计: " + cryCount);
        }

        if (tempAlarmCounter.onValue(status.tempAlarm, now)) {
            tempAlarmCount++;
            hasEvent = true;
            Log.d(TAG, "检测到高温事件, 累计: " + tempAlarmCount);
        }

        // 更新最后哭声时间（即使不是边沿）
        if (status.cryAlarm == 1 && lastCryTime == 0) {
            lastCryTime = now;
        }

        return hasEvent;
    }

    // ===== Getters =====

    public synchronized double getAvgTemp() {
        return Math.round(avgTemp * 10) / 10.0; // 保留1位小数
    }

    public synchronized double getMaxTemp() {
        return Math.round(maxTemp * 10) / 10.0;
    }

    public synchronized int getWetCount() {
        return wetCount;
    }

    public synchronized int getCryCount() {
        return cryCount;
    }

    public synchronized int getTempAlarmCount() {
        return tempAlarmCount;
    }

    public synchronized int getLastCryMinutesAgo() {
        if (lastCryTime == 0)
            return -1;
        return (int) ((System.currentTimeMillis() - lastCryTime) / 60000);
    }

    public synchronized int getSampleCount() {
        return tempSamples.size();
    }

    /**
     * 重置统计（用于新的统计周期）
     */
    public synchronized void reset() {
        tempSamples.clear();
        wetCounter.reset();
        cryCounter.reset();
        tempAlarmCounter.reset();
        avgTemp = 0;
        maxTemp = 0;
        wetCount = 0;
        cryCount = 0;
        tempAlarmCount = 0;
        lastCryTime = 0;
    }

    /**
     * 获取日志摘要
     */
    public synchronized String getSummary() {
        return String.format("Stats60m: avgTemp=%.1f, maxTemp=%.1f, wet=%d, cry=%d, tempAlarm=%d, samples=%d",
                avgTemp, maxTemp, wetCount, cryCount, tempAlarmCount, tempSamples.size());
    }

    // ===== 内部类 =====

    private static class TempSample {
        long timestamp;
        double temp;

        TempSample(long timestamp, double temp) {
            this.timestamp = timestamp;
            this.temp = temp;
        }
    }

    /**
     * 边沿计数器（带去抖）
     */
    private static class EdgeCounter {
        private final long debounceMs;
        private int lastValue = 0;
        private long lastEventTime = 0;

        EdgeCounter(long debounceMs) {
            this.debounceMs = debounceMs;
        }

        /**
         * @return true 如果这是一个新的边沿事件
         */
        boolean onValue(int value, long timestamp) {
            // 检测 0→1 边沿
            if (lastValue == 0 && value == 1) {
                // 检查去抖
                if (timestamp - lastEventTime > debounceMs) {
                    lastEventTime = timestamp;
                    lastValue = value;
                    return true;
                }
            }
            lastValue = value;
            return false;
        }

        void reset() {
            lastValue = 0;
            lastEventTime = 0;
        }
    }
}
