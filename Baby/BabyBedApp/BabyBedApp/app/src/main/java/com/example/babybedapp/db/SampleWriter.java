package com.example.babybedapp.db;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 限频写入器
 * 实现 5秒/值变化 的写入策略
 */
public class SampleWriter {
    private static final String TAG = "SampleWriter";
    private static final long WRITE_INTERVAL_MS = 5000; // 5秒
    private static final long CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000; // 24小时
    private static final long DATA_RETENTION_MS = 3L * 24 * 60 * 60 * 1000; // 3天

    private final AppDatabase database;
    private final ExecutorService executor;

    private StatusSample lastWrittenSample;
    private long lastWriteTime = 0;
    private long lastCleanupTime = 0;

    public SampleWriter(AppDatabase database) {
        this.database = database;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 尝试写入样本（限频）
     * 
     * @return true 如果实际写入了
     */
    public void tryWrite(StatusSample sample) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            boolean shouldWrite = false;

            // 策略1: 5秒间隔写入
            if (now - lastWriteTime >= WRITE_INTERVAL_MS) {
                shouldWrite = true;
            }

            // 策略2: 值变化写入
            if (sample.hasValueChanged(lastWrittenSample)) {
                shouldWrite = true;
            }

            if (shouldWrite) {
                try {
                    long id = database.statusSampleDao().insert(sample);
                    lastWrittenSample = sample;
                    lastWriteTime = now;
                    Log.d(TAG, "写入样本 id=" + id + ", temp=" + (sample.temp_x10 / 10.0));
                } catch (Exception e) {
                    Log.e(TAG, "写入失败: " + e.getMessage());
                }
            }

            // 定期清理
            maybeCleanup(now);
        });
    }

    /**
     * 写入事件
     */
    public void writeEvent(Event event) {
        executor.execute(() -> {
            try {
                long id = database.eventDao().insert(event);
                Log.d(TAG, "写入事件 id=" + id + ", type=" + event.type);
            } catch (Exception e) {
                Log.e(TAG, "写入事件失败: " + e.getMessage());
            }
        });
    }

    /**
     * 定期清理旧数据
     */
    private void maybeCleanup(long now) {
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }

        lastCleanupTime = now;
        long cutoffTs = now - DATA_RETENTION_MS;

        try {
            int samplesDeleted = database.statusSampleDao().deleteOlderThan(cutoffTs);
            int eventsDeleted = database.eventDao().deleteOlderThan(cutoffTs);
            long hoursCutoff = now - (72 * 60 * 60 * 1000); // 72小时
            int statsDeleted = database.hourlyStatDao().deleteOlderThan(hoursCutoff);

            Log.d(TAG, String.format("清理完成: 样本=%d, 事件=%d, 小时统计=%d",
                    samplesDeleted, eventsDeleted, statsDeleted));
        } catch (Exception e) {
            Log.e(TAG, "清理失败: " + e.getMessage());
        }
    }

    /**
     * 强制清理（可在启动时调用）
     */
    public void forceCleanup() {
        executor.execute(() -> maybeCleanup(System.currentTimeMillis()));
    }
}
