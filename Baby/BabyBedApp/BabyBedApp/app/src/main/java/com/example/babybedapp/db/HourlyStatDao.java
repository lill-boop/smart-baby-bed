package com.example.babybedapp.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * 小时统计 DAO
 */
@Dao
public interface HourlyStatDao {

    /**
     * 插入或更新（upsert）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(HourlyStat stat);

    /**
     * 获取时间范围内的统计
     */
    @Query("SELECT * FROM hourly_stats WHERE hour_start_ts >= :startTs ORDER BY hour_start_ts")
    List<HourlyStat> getSince(long startTs);

    /**
     * 获取最近 72 小时的统计
     */
    @Query("SELECT * FROM hourly_stats WHERE hour_start_ts >= :cutoffTs ORDER BY hour_start_ts DESC")
    List<HourlyStat> getRecent72Hours(long cutoffTs);

    /**
     * 获取特定小时的统计
     */
    @Query("SELECT * FROM hourly_stats WHERE device_id = :deviceId AND hour_start_ts = :hourStartTs LIMIT 1")
    HourlyStat getByHour(String deviceId, long hourStartTs);

    /**
     * 清理旧数据
     */
    @Query("DELETE FROM hourly_stats WHERE hour_start_ts < :cutoffTs")
    int deleteOlderThan(long cutoffTs);

    /**
     * 获取记录数量
     */
    @Query("SELECT COUNT(*) FROM hourly_stats")
    int getCount();
}
