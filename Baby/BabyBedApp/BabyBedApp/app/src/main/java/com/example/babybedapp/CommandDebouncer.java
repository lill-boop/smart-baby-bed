package com.example.babybedapp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 命令防抖器
 * 300ms 内只保留最后一次命令，避免用户快速连点造成多次 publish
 */
public class CommandDebouncer {
    private static final String TAG = "CommandDebouncer";
    private static final long DEBOUNCE_DELAY_MS = 300; // 300ms

    private final Handler handler;
    private Runnable pendingRunnable;
    private String pendingPayload;

    public interface OnCommandReadyListener {
        void onCommandReady(String payload);
    }

    public CommandDebouncer() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * 提交命令（300ms 防抖）
     * 
     * @param payload  要发送的 JSON 字符串
     * @param listener 命令准备好时的回调
     */
    public void postCommand(String payload, OnCommandReadyListener listener) {
        // 取消之前待发送的命令
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
            Log.d(TAG, "取消之前的待发送命令: " + pendingPayload);
        }

        // 保存新命令
        pendingPayload = payload;
        pendingRunnable = () -> {
            Log.d(TAG, "防抖完成，发送命令: " + pendingPayload);
            if (listener != null) {
                listener.onCommandReady(pendingPayload);
            }
            pendingRunnable = null;
            pendingPayload = null;
        };

        // 延迟 300ms 执行
        handler.postDelayed(pendingRunnable, DEBOUNCE_DELAY_MS);
        Log.d(TAG, "命令已入队，等待 300ms: " + payload);
    }

    /**
     * 取消所有待发送的命令
     */
    public void cancel() {
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
            Log.d(TAG, "已取消待发送命令");
            pendingRunnable = null;
            pendingPayload = null;
        }
    }

    /**
     * 检查是否有待发送的命令
     */
    public boolean hasPendingCommand() {
        return pendingRunnable != null;
    }
}
