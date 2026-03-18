package com.example.babybedapp.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * 事件 DAO
 */
@Dao
public interface EventDao {

    @Insert
    long insert(Event event);

    /**
     * 按类型和时间范围统计次数
     */
    @Query("SELECT COUNT(*) FROM events WHERE type = :type AND ts >= :startTs AND ts <= :endTs")
    int countByTypeInRange(String type, long startTs, long endTs);

    /**
     * 获取最近1小时各类型次数
     */
    @Query("SELECT COUNT(*) FROM events WHERE type = :type AND ts >= :startTs")
    int countByTypeSince(String type, long startTs);

    /**
     * 按小时聚合事件次数（用于趋势图）
     */
    @Query("SELECT (ts / 3600000) * 3600000 as hour_ts, COUNT(*) as count " +
            "FROM events " +
            "WHERE type = :type AND ts >= :startTs " +
            "GROUP BY (ts / 3600000) " +
            "ORDER BY hour_ts")
    List<HourlyEventCount> getHourlyCount(String type, long startTs);

    /**
     * 获取最近 N 条事件
     */
    @Query("SELECT * FROM events ORDER BY ts DESC LIMIT :limit")
    List<Event> getRecent(int limit);

    /**
     * 获取某类型最后一条事件
     */
    @Query("SELECT * FROM events WHERE type = :type ORDER BY ts DESC LIMIT 1")
    Event getLastByType(String type);

    /**
     * 清理旧数据
     */
    @Query("DELETE FROM events WHERE ts < :cutoffTs")
    int deleteOlderThan(long cutoffTs);

    /**
     * 小时事件统计结果
     */
    class HourlyEventCount {
        public long hour_ts;
        public int count;
    }
}
