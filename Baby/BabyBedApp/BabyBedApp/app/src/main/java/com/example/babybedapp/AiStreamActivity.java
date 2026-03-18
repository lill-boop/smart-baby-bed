package com.example.babybedapp;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * AI Stream Activity
 * Displays the AI-processed MJPEG stream from the Orange Pi.
 */
public class AiStreamActivity extends AppCompatActivity {

    private WebView webView;
    private TextView tvStatus;
    private android.widget.ImageButton btnClose;
    private SocConfig socConfig;
    private IntercomManager intercomManager;
    private static final int STREAM_PORT = 8088;
    private static final String STREAM_PATH = "/stream";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_ai_stream);

        socConfig = new SocConfig(this);

        initViews();
        loadStream();

        // Init Intercom
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
                    intercomManager.startListen();
                    return true;
            }
            return false;
        });
    }

    private void initViews() {
        webView = findViewById(R.id.webViewAi);
        tvStatus = findViewById(R.id.tvAiTitle);
        btnClose = findViewById(R.id.btnCloseAi);

        btnClose.setOnClickListener(v -> finish());

        // Configure WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Set background to black
        webView.setBackgroundColor(0xFF000000);

        webView.setWebViewClient(new WebViewClient());
    }

    private void loadStream() {
        String ip = socConfig.getSocIp();
        String url = "http://" + ip + ":" + STREAM_PORT + STREAM_PATH;

        tvStatus.setText("AI 监控连接中: " + ip);

        // Use an HTML wrapper to center and scale the image
        String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes'>"
                +
                "<style>body{margin:0;padding:0;background-color:black;width:100%;height:100%;display:flex;justify-content:center;align-items:center;}"
                +
                "img{width:100%;height:100%;object-fit:contain;}</style></head>" +
                "<body>" +
                "<img src='" + url + "' />" +
                "</body></html>";

        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // WebView handles orientation changes automatically mostly,
        // but we reload to ensure proper scaling
        loadStream();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (intercomManager != null) {
            intercomManager.startListen();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (intercomManager != null) {
            intercomManager.stopListen();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (intercomManager != null) {
            intercomManager.release();
        }
    }
}
