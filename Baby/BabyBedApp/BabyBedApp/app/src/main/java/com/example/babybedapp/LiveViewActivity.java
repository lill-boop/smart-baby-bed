package com.example.babybedapp;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live View Activity
 * Real-time RTSP stream viewer with AI detection overlay
 */
public class LiveViewActivity extends AppCompatActivity {

    private static final String TAG = "LiveViewActivity";
    private static final int RTSP_PORT = 8554;
    private static final String RTSP_PATH = "/babycam";
    private static final int AI_REFRESH_INTERVAL = 2000; // 2 seconds

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView tvTitle;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private View btnClose;
    private View btnRotate;
    private Button btnRetry;

    // AI Detection (Removed)

    private SocConfig socConfig;
    private boolean isLandscape = false;
    private IntercomManager intercomManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_live_view);

        socConfig = new SocConfig(this);
        initViews();
        initPlayer();

        // Init Intercom (Singleton)
        intercomManager = IntercomManager.getInstance();
        intercomManager.init();
        setupIntercomButton();
    }

    private void setupIntercomButton() {
        Button btnTalk = findViewById(R.id.btnTalkOverlay);
        TextView tvIntercomStatus = findViewById(R.id.tvIntercomStatusOverlay);

        if (btnTalk == null)
            return;

        btnTalk.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    tvIntercomStatus.setText("正在发送语音...");
                    tvIntercomStatus.setTextColor(android.graphics.Color.RED);
                    btnTalk.setText("松开 结束");
                    btnTalk.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
                    intercomManager.startTalk();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    tvIntercomStatus.setText("正在收听环境音...");
                    tvIntercomStatus.setTextColor(android.graphics.Color.WHITE);
                    btnTalk.setText("按住 说话");
                    btnTalk.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF9800")));
                    intercomManager.stopTalk();
                    // Resume listening immediately
                    intercomManager.startListen();
                    return true;
            }
            return false;
        });
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        tvTitle = findViewById(R.id.tvLiveTitle);
        tvStatus = findViewById(R.id.tvLiveStatus);
        progressBar = findViewById(R.id.progressBarLive);
        btnClose = findViewById(R.id.btnCloseLive);
        btnRotate = findViewById(R.id.btnRotateLive);
        btnRetry = findViewById(R.id.btnRetry);

        btnClose.setOnClickListener(v -> finish());
        btnRotate.setOnClickListener(v -> toggleOrientation());
        btnRetry.setOnClickListener(v -> retryConnection());
    }

    private String getRtspUrl() {
        return "rtsp://" + socConfig.getSocIp() + ":" + RTSP_PORT + RTSP_PATH;
    }

    private void initPlayer() {
        String rtspUrl = getRtspUrl();
        Log.d(TAG, "Connecting to RTSP: " + rtspUrl);

        tvStatus.setText("正在连接: " + socConfig.getSocIp());
        progressBar.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.GONE);

        LoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(100, 500, 100, 100)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build();
        playerView.setPlayer(player);

        RtspMediaSource mediaSource = new RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(Uri.parse(rtspUrl)));

        player.setMediaSource(mediaSource);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                runOnUiThread(() -> {
                    switch (state) {
                        case Player.STATE_BUFFERING:
                            tvStatus.setText("正在缓冲...");
                            progressBar.setVisibility(View.VISIBLE);
                            break;
                        case Player.STATE_READY:
                            tvStatus.setText("直播中 🔴");
                            progressBar.setVisibility(View.GONE);
                            break;
                        case Player.STATE_ENDED:
                            tvStatus.setText("直播已结束");
                            break;
                        case Player.STATE_IDLE:
                            break;
                    }
                });
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                runOnUiThread(() -> {
                    tvStatus.setText("连接失败: " + error.getMessage());
                    progressBar.setVisibility(View.GONE);
                    btnRetry.setVisibility(View.VISIBLE);
                    Toast.makeText(LiveViewActivity.this,
                            "RTSP连接失败，请检查网络", Toast.LENGTH_SHORT).show();
                });
            }
        });

        player.prepare();
        player.play();
    }

    private void retryConnection() {
        if (player != null) {
            player.release();
        }
        initPlayer();
    }

    private void toggleOrientation() {
        isLandscape = !isLandscape;
        if (isLandscape) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
        if (intercomManager != null) {
            intercomManager.stopListen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
        if (intercomManager != null) {
            intercomManager.startListen();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (intercomManager != null) {
            intercomManager.release();
        }
    }
}
