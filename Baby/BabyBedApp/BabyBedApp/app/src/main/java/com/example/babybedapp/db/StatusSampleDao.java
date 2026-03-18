package com.example.babybedapp.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * 状态样本 DAO
 */
@Dao
public interface StatusSampleDao {

    @Insert
    long insert(StatusSample sample);

    /**
     * 按时间范围查询
     */
    @Query("SELECT * FROM status_samples WHERE ts >= :startTs AND ts <= :endTs ORDER BY ts DESC")
    List<StatusSample> getByTimeRange(long startTs, long endTs);

    /**
     * 查询最近 N 条
     */
    @Query("SELECT * FROM status_samples ORDER BY ts DESC LIMIT :limit")
    List<StatusSample> getRecent(int limit);

    /**
     * 获取最近一条
     */
    @Query("SELECT * FROM status_samples ORDER BY ts DESC LIMIT 1")
    StatusSample getLatest();

    /**
     * 按小时聚合平均温度
     */
    @Query("SELECT AVG(temp_x10) / 10.0 as avg_temp, " +
            "(ts / 3600000) * 3600000 as hour_ts " +
            "FROM status_samples " +
            "WHERE ts >= :startTs " +
            "GROUP BY (ts / 3600000) " +
            "ORDER BY hour_ts")
    List<HourlyAvgTemp> getHourlyAvgTemp(long startTs);

    /**
     * 获取时间范围内的平均温度
     */
    @Query("SELECT AVG(temp_x10) / 10.0 FROM status_samples WHERE ts >= :startTs AND ts <= :endTs")
    Double getAvgTempInRange(long startTs, long endTs);

    /**
     * 获取时间范围内的最高温度
     */
    @Query("SELECT MAX(temp_x10) / 10.0 FROM status_samples WHERE ts >= :startTs AND ts <= :endTs")
    Double getMaxTempInRange(long startTs, long endTs);

    /**
     * 清理旧数据（保留最近 N 天）
     */
    @Query("DELETE FROM status_samples WHERE ts < :cutoffTs")
    int deleteOlderThan(long cutoffTs);

    /**
     * 获取记录数量
     */
    @Query("SELECT COUNT(*) FROM status_samples")
    int getCount();

    /**
     * 小时聚合温度结果
     */
    class HourlyAvgTemp {
        public double avg_temp;
        public long hour_ts;
    }
}
