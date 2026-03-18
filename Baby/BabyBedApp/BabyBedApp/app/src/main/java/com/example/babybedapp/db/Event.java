package com.example.babybedapp.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 事件 Entity
 * 记录报警事件（WET/CRY/TEMP_HIGH）的边沿触发
 */
@Entity(tableName = "events")
public class Event {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long ts; // 时间戳(毫秒)
    public String type; // 事件类型: WET, CRY, TEMP_HIGH
    public int value; // 触发时的值(可选)
    public String source; // 来源: status, cmd

    public Event() {
    }

    public static final String TYPE_WET = "WET";
    public static final String TYPE_CRY = "CRY";
    public static final String TYPE_TEMP_HIGH = "TEMP_HIGH";

    public static Event create(String type, int value) {
        Event event = new Event();
        event.ts = System.currentTimeMillis();
        event.type = type;
        event.value = value;
        event.source = "status";
        return event;
    }
}
