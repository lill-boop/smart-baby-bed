package com.example.babybedapp;

/**
 * 设备状态数据模型
 * 保存从 MQTT status 消息解析出的所有状态字段
 */
public class DeviceStatus {
    // 传感器数据
    public double tempC; // 温度 (℃) = temp_x10 / 10.0
    public int tempAlarm; // 温度报警: 1=报警, 0=正常
    public int wetAlarm; // 尿床报警: 1=报警, 0=正常
    public int cryAlarm; // 哭声报警: 1=报警, 0=正常

    // 控制状态
    public int mode; // 0=自动, 1=手动
    public int fanFlag; // 风扇开关
    public int hotFlag; // 加热开关
    public int cribFlag; // 摇床开关
    public int musicFlag; // 音乐播放开关
    public String alert15sMsg; // 15秒危险持续报警消息 (为空表示无)

    // 时间戳
    public long timestamp; // 解析时间 (System.currentTimeMillis())

    public DeviceStatus() {
        this.timestamp = System.currentTimeMillis();
        this.tempC = 0.0;
        this.tempAlarm = 0;
        this.wetAlarm = 0;
        this.cryAlarm = 0;
        this.mode = 0;
        this.fanFlag = 0;
        this.hotFlag = 0;
        this.cribFlag = 0;
        this.musicFlag = 0;
    }

    @Override
    public String toString() {
        return String.format(
                "DeviceStatus{tempC=%.1f, tempAlarm=%d, wetAlarm=%d, cryAlarm=%d, mode=%d, fan=%d, hot=%d, crib=%d, music=%d}",
                tempC, tempAlarm, wetAlarm, cryAlarm, mode, fanFlag, hotFlag, cribFlag, musicFlag);
    }
}
