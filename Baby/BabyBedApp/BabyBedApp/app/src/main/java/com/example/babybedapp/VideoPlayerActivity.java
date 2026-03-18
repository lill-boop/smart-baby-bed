package com.example.babybedapp;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * Video Player Activity
 * Plays mp4 videos from SOC using ExoPlayer
 */
public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayerActivity";

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView tvTitle;
    private Button btnClose;
    private Button btnRotate;
    private Button btnIntercom;

    private IntercomManager intercomManager;

    private String videoUrl;
    private String videoTitle;
    private boolean isLandscape = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_video_player);

        videoUrl = getIntent().getStringExtra("video_url");
        videoTitle = getIntent().getStringExtra("video_title");

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "无效的视频地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        initPlayer();
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        tvTitle = findViewById(R.id.tvVideoTitle);
        btnClose = findViewById(R.id.btnClose);
        btnRotate = findViewById(R.id.btnRotate);
        btnIntercom = findViewById(R.id.btnIntercom);

        tvTitle.setText(videoTitle != null ? videoTitle : "视频播放");

        btnClose.setOnClickListener(v -> finish());
        btnRotate.setOnClickListener(v -> toggleOrientation());

        // Initialize IntercomManager (Singleton)
        intercomManager = IntercomManager.getInstance();
        intercomManager.init();

        // Bind intercom button (press and hold to talk)
        btnIntercom.setOnTouchListener((v, event) -> {
            Log.d(TAG, "Intercom button touched: " + event.getAction());
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                Log.i(TAG, "Starting talk...");
                intercomManager.startTalk();
                btnIntercom.setAlpha(0.7f);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                Log.i(TAG, "Stopping talk...");
                intercomManager.stopTalk();
                btnIntercom.setAlpha(1.0f);
                return true;
            }
            return false;
        });
    }

    private void initPlayer() {
        Log.d(TAG, "Playing: " + videoUrl);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
        player.setMediaItem(mediaItem);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage());
                Toast.makeText(VideoPlayerActivity.this,
                        "播放错误: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    Log.d(TAG, "Player ready");
                } else if (state == Player.STATE_ENDED) {
                    Log.d(TAG, "Playback ended");
                }
            }
        });

        player.prepare();
        player.play();
    }

    private void toggleOrientation() {
        isLandscape = !isLandscape;
        if (isLandscape) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            btnRotate.setText("🔄 竖屏");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            btnRotate.setText("🔄 横屏");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
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
