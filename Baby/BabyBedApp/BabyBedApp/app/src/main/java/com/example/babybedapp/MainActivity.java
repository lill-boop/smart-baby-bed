package com.example.babybedapp;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.babybedapp.api.AnalyzeRequest;
import com.example.babybedapp.api.AnalyzeResponse;
import com.example.babybedapp.api.ApiClient;
import com.example.babybedapp.db.AppDatabase;
import com.example.babybedapp.db.Event;
import com.example.babybedapp.db.HourlyStat;
import com.example.babybedapp.db.SampleWriter;
import com.example.babybedapp.db.StatusSample;
import com.example.babybedapp.stats.AnalyzeScheduler;
import com.example.babybedapp.stats.StatsWindow60m;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements MqttManager.Callback {

    private static final String TAG = "MainActivity";
    private static final String DEVICE_ID = "esp8266_01";
    private static final String TOPIC_CMD = "device/esp8266_01/cmd";
    private static final int PERMISSION_REQ_CODE_AUDIO = 200;
    private static final String NOTIFICATION_CHANNEL_ID = "baby_bed_alarms";
    private static final int NOTIFICATION_ID_TEMP = 1001;
    private static final int NOTIFICATION_ID_WET = 1002;
    private static final int NOTIFICATION_ID_CRY = 1003;

    private MqttManager mqttManager;
    private IntercomManager intercomManager;

    // UI 元素
    private TextView tvConnectionStatus;
    private Switch switchMode;
    private Switch switchFan;
    private Switch switchHot;
    private Switch switchCrib;
    private Switch switchMusic;
    private TextView tvMusicStatus;
    private ImageButton btnSettings;

    // 状态卡片
    private CardView cardTemp, cardWet, cardCry;
    private TextView tvTempValue, tvTempStatus;
    private TextView tvWetStatus, tvCryStatus;
    private TextView tvLastUpdateTime;

    // 统计卡片
    private TextView tvStatsAvgTemp, tvStatsWetCount, tvStatsCryCount;

    // AI 分析
    private CardView cardAnalysis;
    private TextView tvAnalysisRisk, tvAnalysisSummary, tvAnalysisSuggestions, tvAnalysisTime;
    private Button btnAnalyze;

    private Button btnReconnect, btnHistory, btnEventsTimeline, btnLiveView;
    private TextView tvLog;

    // 调试 UI
    private TextView tvCmdStatus;
    private TextView tvLastTx;
    private TextView tvLastRx;
    private Button btnTestCmd;

    // 状态变量
    private int currentMode = 0;
    private int fanFlag = 0;
    private int hotFlag = 0;
    private int cribFlag = 0;
    private int musicFlag = 0; // 音乐播放开关

    // 控制逻辑
    private boolean isUpdatingUi = false;
    private boolean isWaitingForResponse = false;
    private boolean isAnalyzing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 管理器
    private AlarmDebounceManager alarmDebounceManager;
    private CommandDebouncer commandDebouncer;
    private DeviceStatus lastStatus;

    // 数据库和统计
    private AppDatabase database;
    private SampleWriter sampleWriter;
    private StatsWindow60m statsWindow;
    private AnalyzeScheduler analyzeScheduler;
    private ExecutorService executor;

    private String lastRawJson = "";
    private long lastAiAlertTime = 0;
    private AlertDialog currentAlertDialog = null;

    // 超时处理
    private final Runnable timeoutRunnable = () -> {
        if (isWaitingForResponse) {
            isWaitingForResponse = false;
            tvCmdStatus.setText("命令状态: 等待设备响应超时");
            tvCmdStatus.setTextColor(Color.RED);
            addLog("错误: 设备未在1秒内响应!");
        }
    };

    // 统计刷新
    private final Runnable statsRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatsUI();
            handler.postDelayed(this, 30000); // 每30秒刷新
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化数据库和统计
        database = AppDatabase.getInstance(this);
        sampleWriter = new SampleWriter(database);
        statsWindow = new StatsWindow60m();
        executor = Executors.newSingleThreadExecutor();

        // 初始化管理器
        alarmDebounceManager = new AlarmDebounceManager();
        commandDebouncer = new CommandDebouncer();

        // 初始化调度器
        analyzeScheduler = new AnalyzeScheduler(statsWindow, DEVICE_ID, new AnalyzeScheduler.AnalyzeCallback() {
            @Override
            public void onAnalyzeStart() {
                runOnUiThread(() -> setAnalyzing(true));
            }

            @Override
            public void onAnalyzeSuccess(AnalyzeResponse response) {
                runOnUiThread(() -> {
                    setAnalyzing(false);
                    updateAnalysisUI(response);
                });
            }

            @Override
            public void onAnalyzeError(String error) {
                runOnUiThread(() -> {
                    setAnalyzing(false);
                    showAnalysisError(error);
                });
            }
        });

        // 创建通知渠道
        createNotificationChannel();

        initViews();
        setupListeners();

        // 初始化 MQTT
        mqttManager = new MqttManager(this, this);

        // 初始化对讲 (新增)
        intercomManager = IntercomManager.getInstance();
        intercomManager.init();
        checkAudioPermission();
        setupIntercomButton();

        // 请求通知权限
        requestNotificationPermission();

        // 启动时清理旧数据
        sampleWriter.forceCleanup();

        // 启动调度器
        analyzeScheduler.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mqttManager != null) {
            addLog("App 前台 -> 连接中...");
            mqttManager.connect();
        }
        // 启动统计刷新
        handler.post(statsRefreshRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mqttManager != null) {
            addLog("App 后台 -> 断开连接...");
            mqttManager.disconnect();
        }
        handler.removeCallbacks(statsRefreshRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analyzeScheduler.stop();
        executor.shutdown();
        if (intercomManager != null) {
            intercomManager.release();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "婴儿床报警",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("婴儿床体温/尿床/哭声报警通知");
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.POST_NOTIFICATIONS }, 100);
            }
        }
    }

    // 新增: 检查录音权限
    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    PERMISSION_REQ_CODE_AUDIO);
        }
    }

    // 新增: 设置对讲按钮逻辑
    private void setupIntercomButton() {
        Button btnTalk = findViewById(R.id.btnTalk);
        TextView tvIntercomStatus = findViewById(R.id.tvIntercomStatus);

        if (btnTalk == null)
            return;

        btnTalk.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Start Talking
                    tvIntercomStatus.setText("正在发送语音...");
                    tvIntercomStatus.setTextColor(Color.RED);
                    btnTalk.setText("松开 结束");
                    btnTalk.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
                    intercomManager.startTalk();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Stop Talking
                    tvIntercomStatus.setText("准备就绪");
                    tvIntercomStatus.setTextColor(Color.parseColor("#FF9800"));
                    btnTalk.setText("按住 说话");
                    btnTalk.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9800")));
                    intercomManager.stopTalk();
                    return true;
            }
            return false;
        });
    }

    private void initViews() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        switchMode = findViewById(R.id.switchMode);
        switchFan = findViewById(R.id.switchFan);
        switchHot = findViewById(R.id.switchHot);
        switchCrib = findViewById(R.id.switchCrib);
        switchMusic = findViewById(R.id.switchMusic);
        tvMusicStatus = findViewById(R.id.tvMusicStatus);
        btnSettings = findViewById(R.id.btnSettings);

        // 状态卡片
        cardTemp = findViewById(R.id.cardTemp);
        cardWet = findViewById(R.id.cardWet);
        cardCry = findViewById(R.id.cardCry);
        tvTempValue = findViewById(R.id.tvTempValue);
        tvTempStatus = findViewById(R.id.tvTempStatus);
        tvWetStatus = findViewById(R.id.tvWetStatus);
        tvCryStatus = findViewById(R.id.tvCryStatus);
        tvLastUpdateTime = findViewById(R.id.tvLastUpdateTime);

        // 统计卡片
        tvStatsAvgTemp = findViewById(R.id.tvStatsAvgTemp);
        tvStatsWetCount = findViewById(R.id.tvStatsWetCount);
        tvStatsCryCount = findViewById(R.id.tvStatsCryCount);

        // AI 分析
        cardAnalysis = findViewById(R.id.cardAnalysis);
        tvAnalysisRisk = findViewById(R.id.tvAnalysisRisk);
        tvAnalysisSummary = findViewById(R.id.tvAnalysisSummary);
        tvAnalysisSuggestions = findViewById(R.id.tvAnalysisSuggestions);
        tvAnalysisTime = findViewById(R.id.tvAnalysisTime);
        btnAnalyze = findViewById(R.id.btnAnalyze);

        btnReconnect = findViewById(R.id.btnReconnect);
        btnHistory = findViewById(R.id.btnHistory);
        btnEventsTimeline = findViewById(R.id.btnEventsTimeline);
        btnLiveView = findViewById(R.id.btnLiveView);
        tvLog = findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        // 调试 UI
        tvCmdStatus = findViewById(R.id.tvCmdStatus);
        tvLastTx = findViewById(R.id.tvLastTx);
        tvLastRx = findViewById(R.id.tvLastRx);
        btnTestCmd = findViewById(R.id.btnTestCmd);

        updateControlsState();
    }

    private void updateControlsState() {
        boolean isManual = (currentMode == 1);
        switchFan.setEnabled(isManual);
        switchHot.setEnabled(isManual);
        switchCrib.setEnabled(isManual);

        if (!isManual) {
            switchFan.setText("风扇 (自动)");
            switchHot.setText("加热 (自动)");
            switchCrib.setText("摇床 (自动)");
        } else {
            switchFan.setText("风扇控制");
            switchHot.setText("加热控制");
            switchCrib.setText("摇床控制");
        }
    }

    private void setupListeners() {
        // 设置按钮 - 跳转到设置页
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        btnReconnect.setOnClickListener(v -> {
            addLog("手动重连请求...");
            mqttManager.connect();
        });

        btnHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });

        // 事件时间轴按钮
        btnEventsTimeline.setOnClickListener(v -> {
            startActivity(new Intent(this, EventsActivity.class));
        });

        // 实时预览按钮
        btnLiveView.setOnClickListener(v -> {
            startActivity(new Intent(this, LiveViewActivity.class));
        });

        // AI 监控按钮
        Button btnAiStream = findViewById(R.id.btnAiStream);
        btnAiStream.setOnClickListener(v -> {
            startActivity(new Intent(this, AiStreamActivity.class));
        });

        btnAnalyze.setOnClickListener(v -> {
            if (!isAnalyzing) {
                analyzeNow();
            }
        });

        // 音乐控制开关
        switchMusic.setOnClickListener(v -> {
            if (isUpdatingUi)
                return;
            musicFlag = switchMusic.isChecked() ? 1 : 0;
            sendMusicControl();
        });

        btnTestCmd.setOnClickListener(v -> {
            currentMode = 1;
            fanFlag = 1;
            hotFlag = 0;
            cribFlag = 0;

            isUpdatingUi = true;
            switchMode.setChecked(true);
            switchFan.setChecked(true);
            switchHot.setChecked(false);
            switchCrib.setChecked(false);
            updateControlsState();
            isUpdatingUi = false;

            addLog("发送测试命令 (风扇开启)...");
            sendFullControlJson();
        });

        // 模式开关
        switchMode.setOnClickListener(v -> {
            if (isUpdatingUi)
                return;
            currentMode = switchMode.isChecked() ? 1 : 0;
            updateControlsState();
            sendFullControlJson();
        });

        // 设备开关
        Switch[] switches = { switchFan, switchHot, switchCrib };
        for (Switch s : switches) {
            s.setOnClickListener(v -> {
                if (isUpdatingUi)
                    return;

                if (currentMode == 0) {
                    s.setChecked(!s.isChecked());
                    Toast.makeText(this, "请先切换到手动模式!", Toast.LENGTH_SHORT).show();
                    return;
                }

                fanFlag = switchFan.isChecked() ? 1 : 0;
                hotFlag = switchHot.isChecked() ? 1 : 0;
                cribFlag = switchCrib.isChecked() ? 1 : 0;

                sendFullControlJson();
            });
        }
    }

    private void sendFullControlJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("mode", currentMode);
            json.put("fan_flag", fanFlag);
            json.put("hot_flag", hotFlag);
            json.put("crib_flag", cribFlag);
            json.put("music_flag", musicFlag); // 添加音乐标志

            String payload = json.toString();

            commandDebouncer.postCommand(payload, finalPayload -> {
                isWaitingForResponse = true;
                handler.removeCallbacks(timeoutRunnable);
                handler.postDelayed(timeoutRunnable, 1000);

                tvCmdStatus.setText("命令状态: 等待响应...");
                tvCmdStatus.setTextColor(Color.parseColor("#FFA500"));
                tvLastTx.setText("发送: " + finalPayload);

                mqttManager.publish(TOPIC_CMD, finalPayload);
            });

        } catch (JSONException e) {
            addLog("JSON 构建错误: " + e.getMessage());
        }
    }

    /**
     * 发送音乐控制命令
     */
    private void sendMusicControl() {
        try {
            JSONObject json = new JSONObject();
            json.put("music_flag", musicFlag);

            String payload = json.toString();
            addLog("发送音乐控制: " + (musicFlag == 1 ? "播放" : "停止"));
            tvMusicStatus.setText(musicFlag == 1 ? "正在播放安抚音乐..." : "音乐已停止");
            tvLastTx.setText("发送: " + payload);

            mqttManager.publish(TOPIC_CMD, payload);

        } catch (JSONException e) {
            addLog("音乐控制 JSON 错误: " + e.getMessage());
        }
    }

    // --- 分析功能 ---

    private void analyzeNow() {
        setAnalyzing(true);

        AnalyzeRequest request = AnalyzeRequest.fromStats(
                DEVICE_ID,
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
                runOnUiThread(() -> {
                    setAnalyzing(false);
                    if (response.isSuccessful() && response.body() != null) {
                        AnalyzeResponse result = response.body();
                        Log.d(TAG, "分析成功: risk=" + result.risk + ", summary=" + result.summary);
                        updateAnalysisUI(result);
                        Toast.makeText(MainActivity.this,
                                result.getRiskText() + ": " + result.summary,
                                Toast.LENGTH_LONG).show();
                    } else {
                        showAnalysisError("服务器错误: " + response.code());
                    }
                });
            }

            @Override
            public void onFailure(Call<AnalyzeResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    setAnalyzing(false);
                    String error = "网络错误: " + t.getMessage();
                    Log.e(TAG, error);
                    showAnalysisError(error);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setAnalyzing(boolean analyzing) {
        isAnalyzing = analyzing;
        btnAnalyze.setEnabled(!analyzing);
        btnAnalyze.setText(analyzing ? "分析中..." : "分析最近1小时");
    }

    private void updateAnalysisUI(AnalyzeResponse response) {
        cardAnalysis.setCardBackgroundColor(response.getRiskColor());
        tvAnalysisRisk.setText("风险: " + response.getRiskText());
        tvAnalysisRisk.setTextColor(Color.WHITE);
        tvAnalysisSummary.setText(response.summary != null ? response.summary : "");
        tvAnalysisSummary.setTextColor(Color.WHITE);

        if (response.suggestions != null && !response.suggestions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int count = Math.min(3, response.suggestions.size());
            for (int i = 0; i < count; i++) {
                sb.append("• ").append(response.suggestions.get(i));
                if (i < count - 1)
                    sb.append("\n");
            }
            tvAnalysisSuggestions.setText(sb.toString());
            tvAnalysisSuggestions.setTextColor(Color.WHITE);
            tvAnalysisSuggestions.setVisibility(View.VISIBLE);
        } else {
            tvAnalysisSuggestions.setVisibility(View.GONE);
        }

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        tvAnalysisTime.setText("最近: " + time);
        tvAnalysisTime.setTextColor(Color.WHITE);
    }

    private void showAnalysisError(String error) {
        cardAnalysis.setCardBackgroundColor(Color.parseColor("#9E9E9E"));
        tvAnalysisRisk.setText("风险: 分析失败");
        tvAnalysisSummary.setText(error);
        tvAnalysisSuggestions.setVisibility(View.GONE);
    }

    // --- MQTT 回调 ---

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("已连接");
            tvConnectionStatus.setBackgroundColor(Color.parseColor("#4CAF50"));
            tvConnectionStatus.setTextColor(Color.WHITE);
            addLog("已连接到 Broker");
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("未连接");
            tvConnectionStatus.setBackgroundColor(Color.RED);
            tvConnectionStatus.setTextColor(Color.WHITE);
            addLog("已断开 Broker 连接");
        });
    }

    @Override
    public void onMessageReceived(String topic, String message) {
        lastRawJson = message;
        runOnUiThread(() -> {
            tvLastRx.setText("最后接收: " + message);
            Log.d(TAG, "RX [" + topic + "]: " + message);

            // --- AI Camera Topic ---
            if (topic.equals("babycam/ai/status")) {
                handleAiStatusMessage(message);
                return;
            }

            // --- STM32/ESP8266 Device Topic ---
            DeviceStatus status = StatusParser.parse(message);
            if (status == null) {
                addLog("JSON 解析失败，跳过");
                return;
            }

            lastStatus = status;

            // 写入数据库
            StatusSample sample = StatusSample.fromDeviceStatus(status, message);
            sampleWriter.tryWrite(sample);

            // 更新内存统计
            boolean hasEvent = statsWindow.onStatus(status);
            Log.d(TAG, statsWindow.getSummary());

            // 如果有事件，写入事件表并触发分析
            if (hasEvent) {
                writeEventsIfNeeded(status);
                analyzeScheduler.onEventTriggered();
            }

            // 更新统计 UI
            updateStatsUI();

            // 更新控制开关
            isUpdatingUi = true;
            currentMode = status.mode;
            switchMode.setChecked(currentMode == 1);
            updateControlsState();

            fanFlag = status.fanFlag;
            hotFlag = status.hotFlag;
            cribFlag = status.cribFlag;
            switchFan.setChecked(fanFlag == 1);
            switchHot.setChecked(hotFlag == 1);
            switchCrib.setChecked(cribFlag == 1);
            isUpdatingUi = false;

            // 更新状态卡片
            updateStatusCards(status);

            // 检查并显示报警弹窗
            checkAndShowAlarms(status);

            // 命令验证检查
            if (isWaitingForResponse) {
                if (status.mode == currentMode &&
                        status.fanFlag == fanFlag &&
                        status.hotFlag == hotFlag &&
                        status.cribFlag == cribFlag) {

                    isWaitingForResponse = false;
                    handler.removeCallbacks(timeoutRunnable);
                    tvCmdStatus.setText("命令状态: 成功 (已验证)");
                    tvCmdStatus.setTextColor(Color.parseColor("#008000"));
                }
            }

            // 尝试保存小时统计
            maybeSaveHourlyStat();
        });
    }

    private void writeEventsIfNeeded(DeviceStatus status) {
        executor.execute(() -> {
            // 这里简化处理，边沿检测已在 StatsWindow60m 中完成
            // 实际事件写入由 AlarmDebounceManager 控制
        });
    }

    private void maybeSaveHourlyStat() {
        executor.execute(() -> {
            try {
                long hourStart = HourlyStat.getCurrentHourStart();
                HourlyStat stat = new HourlyStat();
                stat.device_id = DEVICE_ID;
                stat.hour_start_ts = hourStart;
                stat.avg_temp = statsWindow.getAvgTemp();
                stat.max_temp = statsWindow.getMaxTemp();
                stat.wet_count = statsWindow.getWetCount();
                stat.cry_count = statsWindow.getCryCount();
                stat.temp_alarm_count = statsWindow.getTempAlarmCount();

                database.hourlyStatDao().upsert(stat);
            } catch (Exception e) {
                Log.e(TAG, "保存小时统计失败: " + e.getMessage());
            }
        });
    }

    @Override
    public void onLog(String message) {
        runOnUiThread(() -> addLog(message));
    }

    // --- 统计 UI 更新 ---

    private void updateStatsUI() {
        double avgTemp = statsWindow.getAvgTemp();
        if (avgTemp > 0) {
            tvStatsAvgTemp.setText(String.format(Locale.getDefault(), "%.1f ℃", avgTemp));
        } else {
            tvStatsAvgTemp.setText("--.- ℃");
        }
        tvStatsWetCount.setText(String.valueOf(statsWindow.getWetCount()));
        tvStatsCryCount.setText(String.valueOf(statsWindow.getCryCount()));
    }

    // --- 状态卡片更新 ---

    private void updateStatusCards(DeviceStatus status) {
        if (status.tempC > 0) {
            tvTempValue.setText(String.format(Locale.getDefault(), "%.1f ℃", status.tempC));
        } else {
            tvTempValue.setText("--.- ℃");
        }

        if (status.tempAlarm == 1) {
            setCardAlarm(cardTemp, tvTempStatus, "报警");
        } else {
            setCardNormal(cardTemp, tvTempStatus, "正常");
        }

        if (status.wetAlarm == 1) {
            setCardAlarm(cardWet, tvWetStatus, "尿床报警");
        } else {
            setCardNormal(cardWet, tvWetStatus, "正常");
        }

        if (status.cryAlarm == 1) {
            setCardAlarm(cardCry, tvCryStatus, "哭声报警");
        } else {
            setCardNormal(cardCry, tvCryStatus, "正常");
        }

        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(status.timestamp));
        tvLastUpdateTime.setText("最后更新: " + time);

        flashCard(cardTemp);
    }

    private void setCardAlarm(CardView card, TextView statusText, String text) {
        card.setCardBackgroundColor(Color.parseColor("#F44336"));
        statusText.setText(text);
    }

    private void setCardNormal(CardView card, TextView statusText, String text) {
        card.setCardBackgroundColor(Color.parseColor("#4CAF50"));
        statusText.setText(text);
    }

    private void flashCard(CardView card) {
        int colorFrom = Color.WHITE;
        int colorTo = card.getCardBackgroundColor().getDefaultColor();
        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnim.setDuration(300);
        colorAnim.addUpdateListener(animator -> card.setCardBackgroundColor((int) animator.getAnimatedValue()));
        colorAnim.start();
    }

    // --- 报警弹窗 ---

    private void checkAndShowAlarms(DeviceStatus status) {
        if (alarmDebounceManager.shouldShowPopup(AlarmDebounceManager.AlarmType.TEMP, status.tempAlarm)) {
            String content = String.format(Locale.getDefault(), "当前温度: %.1f ℃", status.tempC);
            showAlarmDialog("体温过高", content);
            sendNotification(NOTIFICATION_ID_TEMP, "体温过高", content);
            // 写入事件
            sampleWriter.writeEvent(Event.create(Event.TYPE_TEMP_HIGH, (int) (status.tempC * 10)));
        }

        if (alarmDebounceManager.shouldShowPopup(AlarmDebounceManager.AlarmType.WET, status.wetAlarm)) {
            showAlarmDialog("尿床提醒", "检测到尿床情况，请及时处理!");
            sendNotification(NOTIFICATION_ID_WET, "尿床提醒", "检测到尿床情况，请及时处理!");
            sampleWriter.writeEvent(Event.create(Event.TYPE_WET, 1));
        }

        if (alarmDebounceManager.shouldShowPopup(AlarmDebounceManager.AlarmType.CRY, status.cryAlarm)) {
            showAlarmDialog("哭声提醒", "检测到婴儿哭声，请查看!");
            sendNotification(NOTIFICATION_ID_CRY, "哭声提醒", "检测到婴儿哭声，请查看!");
            sampleWriter.writeEvent(Event.create(Event.TYPE_CRY, 1));
        }

        // 15秒严重报警 (Face Down / Covering)
        if (status.alert15sMsg != null && !status.alert15sMsg.isEmpty()) {
            // 这里的 debounce 简单处理：如果正在响铃就不再弹，或者直接弹
            // 因为这是严重报警，建议强制弹
            if (alarmDebounceManager.shouldShowPopup(AlarmDebounceManager.AlarmType.CRY, 1)) { // 复用 CRY 的冷却或者独立的
                showAlarmDialog("⚠️ 严重安全警报", "检测到: " + status.alert15sMsg + " 持续超过15秒！请立即检查！");
                sendNotification(1004, "严重安全警报", "检测到: " + status.alert15sMsg + " 持续超过15秒！");
                // 写入特殊事件
                sampleWriter.writeEvent(Event.create(Event.TYPE_CRY, 2));
            }
        }
    }

    private void showAlarmDialog(String title, String message) {
        if (currentAlertDialog != null && currentAlertDialog.isShowing()) {
            return; // 防止报警堆叠
        }
        currentAlertDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("知道了", null)
                .setCancelable(true)
                .setOnDismissListener(dialog -> currentAlertDialog = null) // 关闭后置空
                .show();
        addLog("弹窗: " + title);
    }

    private void sendNotification(int notificationId, String title, String content) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "无通知权限，跳过通知");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[] { 0, 500, 200, 500 });

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(notificationId, builder.build());
        addLog("通知: " + title);
    }

    // --- 日志 ---

    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = time + ": " + message + "\n";
        tvLog.append(line);

        if (tvLog.getLayout() != null) {
            int scrollAmount = tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
            if (scrollAmount > 0)
                tvLog.scrollTo(0, scrollAmount);
        }
    }

    // --- AI Camera Status Handler ---
    private void handleAiStatusMessage(String jsonMessage) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(jsonMessage);

            // Check alert_15s field (could be string or boolean)
            String alertMsg = "";
            if (json.has("alert_15s")) {
                Object val = json.get("alert_15s");
                if (val instanceof String) {
                    alertMsg = (String) val;
                } else if (val instanceof Boolean && (Boolean) val) {
                    alertMsg = "检测到危险";
                }
            }

            // Trigger popup if alert is active
            if (alertMsg != null && !alertMsg.isEmpty()) {
                Log.d(TAG, "AI ALERT DETECTED: " + alertMsg);

                long now = System.currentTimeMillis();
                if (now - lastAiAlertTime > 30000) { // 30秒冷却
                    lastAiAlertTime = now;
                    addLog("AI 报警: " + alertMsg);
                    showAlarmDialog("⚠️ 严重安全警报", "检测到: " + alertMsg + " 持续超过3秒！请立即检查！");
                    sendNotification(1004, "严重安全警报", "检测到: " + alertMsg + " 持续超过3秒！");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "AI Status 解析错误: " + e.getMessage());
        }
    }
}
