package com.example.babybedapp.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 小时统计 Entity
 * 按小时聚合存储统计数据
 */
@Entity(tableName = "hourly_stats", indices = { @Index(value = { "device_id", "hour_start_ts" }, unique = true) })
public class HourlyStat {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String device_id; // 设备ID
    public long hour_start_ts; // 小时起始时间戳(整点毫秒)
    public double avg_temp; // 平均温度(°C)
    public double max_temp; // 最高温度(°C)
    public int wet_count; // 尿床次数
    public int cry_count; // 哭泣次数
    public int temp_alarm_count; // 高温报警次数

    public HourlyStat() {
    }

    /**
     * 获取当前小时的起始时间戳
     */
    public static long getCurrentHourStart() {
        long now = System.currentTimeMillis();
        return now - (now % (60 * 60 * 1000)); // 整点
    }
}
