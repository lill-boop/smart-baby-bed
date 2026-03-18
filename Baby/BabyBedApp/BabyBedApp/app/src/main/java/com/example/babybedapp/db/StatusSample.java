package com.example.babybedapp.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 状态样本 Entity
 * 存储每次 MQTT status 消息的原始数据
 */
@Entity(tableName = "status_samples")
public class StatusSample {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long ts; // 时间戳(毫秒)
    public int temp_x10; // 温度*10
    public int temp_alarm; // 温度报警
    public int wet; // 尿床报警
    public int cry; // 哭声报警
    public int mode; // 模式
    public int fan_flag; // 风扇
    public int hot_flag; // 加热
    public int crib_flag; // 摇床
    public String raw_json; // 原始 JSON (可选)

    public StatusSample() {
    }

    public static StatusSample fromDeviceStatus(com.example.babybedapp.DeviceStatus ds, String rawJson) {
        StatusSample sample = new StatusSample();
        sample.ts = ds.timestamp;
        sample.temp_x10 = (int) (ds.tempC * 10);
        sample.temp_alarm = ds.tempAlarm;
        sample.wet = ds.wetAlarm;
        sample.cry = ds.cryAlarm;
        sample.mode = ds.mode;
        sample.fan_flag = ds.fanFlag;
        sample.hot_flag = ds.hotFlag;
        sample.crib_flag = ds.cribFlag;
        sample.raw_json = rawJson;
        return sample;
    }

    /**
     * 判断值是否有变化（用于限频写入）
     */
    public boolean hasValueChanged(StatusSample other) {
        if (other == null)
            return true;
        return this.temp_x10 != other.temp_x10 ||
                this.temp_alarm != other.temp_alarm ||
                this.wet != other.wet ||
                this.cry != other.cry ||
                this.mode != other.mode ||
                this.fan_flag != other.fan_flag ||
                this.hot_flag != other.hot_flag ||
                this.crib_flag != other.crib_flag;
    }
}
