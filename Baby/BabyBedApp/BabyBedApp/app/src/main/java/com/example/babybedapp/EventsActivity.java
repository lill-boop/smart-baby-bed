package com.example.babybedapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Events Timeline Activity
 * Shows list of babycam events with video playback capability
 */
public class EventsActivity extends AppCompatActivity implements EventAdapter.OnEventClickListener {

    private static final String TAG = "EventsActivity";
    private static final int MAX_EVENTS = 200;

    private SocConfig socConfig;
    private EventAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvSocIp;
    private View btnBack;
    private View btnSettings;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_events);

        socConfig = new SocConfig(this);

        initViews();
        setupListeners();
        loadEvents();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerEvents);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvSocIp = findViewById(R.id.tvSocIp);
        btnBack = findViewById(R.id.btnBack);
        btnSettings = findViewById(R.id.btnSettings);

        adapter = new EventAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        updateIpDisplay();
    }

    private void updateIpDisplay() {
        tvSocIp.setText("SOC: " + socConfig.getSocIp());
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(this::loadEvents);
        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SOC 设置");

        final EditText input = new EditText(this);
        input.setText(socConfig.getSocIp());
        input.setHint("输入 SOC IP 地址");
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String newIp = input.getText().toString().trim();
            if (!newIp.isEmpty()) {
                socConfig.setSocIp(newIp);
                updateIpDisplay();
                loadEvents();
                Toast.makeText(this, "IP 已更新: " + newIp, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void loadEvents() {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("加载中...");

        executor.execute(() -> {
            List<BabyCamEvent> events = new ArrayList<>();
            String errorMsg = null;

            try {
                URL url = new URL(socConfig.getEventsUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            try {
                                BabyCamEvent event = BabyCamEvent.fromJson(line);
                                events.add(event);
                            } catch (JSONException e) {
                                Log.w(TAG, "Failed to parse event: " + line);
                            }
                        }
                    }
                    reader.close();
                } else {
                    errorMsg = "HTTP " + conn.getResponseCode();
                }
                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Load events error", e);
                errorMsg = e.getMessage();
            }

            // Sort by timestamp descending (newest first)
            Collections.sort(events, (a, b) -> Long.compare(b.getTs(), a.getTs()));

            // Limit to MAX_EVENTS
            if (events.size() > MAX_EVENTS) {
                events = events.subList(0, MAX_EVENTS);
            }

            final List<BabyCamEvent> finalEvents = events;
            final String finalError = errorMsg;

            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (finalError != null) {
                    tvStatus.setText("加载失败: " + finalError);
                    Toast.makeText(this, "无法连接 SOC: " + finalError, Toast.LENGTH_LONG).show();
                } else if (finalEvents.isEmpty()) {
                    tvStatus.setText("暂无事件记录");
                } else {
                    tvStatus.setText("共 " + finalEvents.size() + " 条事件");
                    adapter.setEvents(finalEvents);
                }
            });
        });
    }

    @Override
    public void onEventClick(BabyCamEvent event) {
        Log.d(TAG, "Event clicked: " + event.getUrl());

        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_url", event.getUrl());
        intent.putExtra("video_title", event.getType() + " - " + event.getFormattedTime());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
