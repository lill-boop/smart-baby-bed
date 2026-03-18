package com.example.babybedapp.api;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * API 客户端单例
 */
public class ApiClient {
    private static final String TAG = "ApiClient";

    /**
     * AI 分析 API 服务器地址（可配置）
     * 默认使用 Tailscale 暴露的地址
     */
    private static final String BASE_URL = "http://192.168.0.222:8000/"; // LAN IP for fast data access

    private static volatile ApiClient INSTANCE;
    private final BabyApi babyApi;

    private ApiClient() {
        // 日志拦截器
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Log.d(TAG, message));
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttp 客户端
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Retrofit 实例
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        babyApi = retrofit.create(BabyApi.class);
        Log.d(TAG, "ApiClient 初始化完成, BASE_URL=" + BASE_URL);
    }

    public static ApiClient getInstance() {
        if (INSTANCE == null) {
            synchronized (ApiClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ApiClient();
                }
            }
        }
        return INSTANCE;
    }

    public BabyApi getBabyApi() {
        return babyApi;
    }
}
