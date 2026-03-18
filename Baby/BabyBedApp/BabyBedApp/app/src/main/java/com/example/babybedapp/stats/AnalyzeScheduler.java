package com.example.babybedapp.stats;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.babybedapp.api.AnalyzeRequest;
import com.example.babybedapp.api.AnalyzeResponse;
import com.example.babybedapp.api.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 分析调度器
 * - 15分钟定时触发
 * - 事件触发（30秒防抖）
 * - 限流（每小时最多6次）
 * - 指数退避重试
 */
public class AnalyzeScheduler {
    private static final String TAG = "AnalyzeScheduler";

    private static final long PERIODIC_INTERVAL_MS = 15 * 60 * 1000; // 15分钟
    private static final long EVENT_DEBOUNCE_MS = 30 * 1000; // 30秒
    private static final int MAX_CALLS_PER_HOUR = 6;
    private static final int MAX_RETRIES = 3;

    private final Handler handler;
    private final StatsWindow60m statsWindow;
    private final String deviceId;
    private final AnalyzeCallback callback;

    // 限流
    private int callsThisHour = 0;
    private long hourStartTime = 0;

    // 防抖
    private Runnable pendingEventAnalysis = null;

    // 重试
    private int retryCount = 0;

    // 定时任务
    private final Runnable periodicTask = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "定时分析触发");
            doAnalyzeIfValid();
            handler.postDelayed(this, PERIODIC_INTERVAL_MS);
        }
    };

    public interface AnalyzeCallback {
        void onAnalyzeStart();

        void onAnalyzeSuccess(AnalyzeResponse response);

        void onAnalyzeError(String error);
    }

    public AnalyzeScheduler(StatsWindow60m statsWindow, String deviceId, AnalyzeCallback callback) {
        this.handler = new Handler(Looper.getMainLooper());
        this.statsWindow = statsWindow;
        this.deviceId = deviceId;
        this.callback = callback;
        this.hourStartTime = System.currentTimeMillis();
    }

    /**
     * 启动定时调度
     */
    public void start() {
        handler.postDelayed(periodicTask, PERIODIC_INTERVAL_MS);
        Log.d(TAG, "调度器已启动，间隔=" + (PERIODIC_INTERVAL_MS / 60000) + "分钟");
    }

    /**
     * 停止调度
     */
    public void stop() {
        handler.removeCallbacks(periodicTask);
        if (pendingEventAnalysis != null) {
            handler.removeCallbacks(pendingEventAnalysis);
        }
        Log.d(TAG, "调度器已停止");
    }

    /**
     * 事件触发分析（带30秒防抖）
     */
    public void onEventTriggered() {
        if (pendingEventAnalysis != null) {
            handler.removeCallbacks(pendingEventAnalysis);
        }

        pendingEventAnalysis = () -> {
            Log.d(TAG, "事件触发分析");
            doAnalyzeIfValid();
            pendingEventAnalysis = null;
        };

        handler.postDelayed(pendingEventAnalysis, EVENT_DEBOUNCE_MS);
        Log.d(TAG, "事件分析已入队，30秒后执行");
    }

    /**
     * 手动触发分析
     */
    public void analyzeNow() {
        Log.d(TAG, "手动触发分析");
        doAnalyze();
    }

    /**
     * 检查是否可以分析
     */
    private boolean canAnalyze() {
        // 检查样本数量
        if (statsWindow.getSampleCount() == 0) {
            Log.d(TAG, "无有效样本，跳过分析");
            return false;
        }

        // 检查限流
        long now = System.currentTimeMillis();
        if (now - hourStartTime > 60 * 60 * 1000) {
            // 新的一小时
            hourStartTime = now;
            callsThisHour = 0;
        }

        if (callsThisHour >= MAX_CALLS_PER_HOUR) {
            Log.d(TAG, "已达到每小时限制 (" + MAX_CALLS_PER_HOUR + " 次)");
            return false;
        }

        return true;
    }

    private void doAnalyzeIfValid() {
        if (canAnalyze()) {
            doAnalyze();
        }
    }

    private void doAnalyze() {
        callsThisHour++;
        retryCount = 0;
        executeAnalyze();
    }

    private void executeAnalyze() {
        if (callback != null) {
            callback.onAnalyzeStart();
        }

        AnalyzeRequest request = AnalyzeRequest.fromStats(
                deviceId,
                statsWindow.getAvgTemp(),
                statsWindow.getMaxTemp(),
                statsWindow.getWetCount(),
                statsWindow.getCryCount(),
                statsWindow.getTempAlarmCount(),
                statsWindow.getLastCryMinutesAgo());

        Log.d(TAG, "发送分析请求: " + request);

        ApiClient.getInstance().getBabyApi().analyze(request).enqueue(new Callback<AnalyzeResponse>() {
            @Override
            public void onResponse(Call<AnalyzeResponse> call, Response<AnalyzeResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AnalyzeResponse result = response.body();
                    Log.d(TAG, "分析成功: " + result);
                    retryCount = 0;
                    if (callback != null) {
                        callback.onAnalyzeSuccess(result);
                    }
                } else {
                    String error = "响应错误: " + response.code();
                    Log.e(TAG, error);
                    handleError(error);
                }
            }

            @Override
            public void onFailure(Call<AnalyzeResponse> call, Throwable t) {
                String error = "网络错误: " + t.getMessage();
                Log.e(TAG, error);
                handleError(error);
            }
        });
    }

    private void handleError(String error) {
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            long delay = (long) Math.pow(2, retryCount) * 1000; // 指数退避: 2s, 4s, 8s
            Log.d(TAG, "将在 " + delay + "ms 后重试 (" + retryCount + "/" + MAX_RETRIES + ")");
            handler.postDelayed(this::executeAnalyze, delay);
        } else {
            Log.e(TAG, "重试次数已用尽");
            if (callback != null) {
                callback.onAnalyzeError(error);
            }
        }
    }
}
