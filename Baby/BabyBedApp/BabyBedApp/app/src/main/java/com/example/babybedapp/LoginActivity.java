package com.example.babybedapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.babybedapp.auth.AuthManager;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 登录页面
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvForgotPassword, tvGoRegister;

    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = AuthManager.getInstance(this);

        // 检查是否已登录
        if (authManager.isLoggedIn()) {
            goToMain();
            return;
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvGoRegister = findViewById(R.id.tvGoRegister);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> doLogin());

        tvGoRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });

        tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(this, "请联系管理员重置密码", Toast.LENGTH_SHORT).show();
        });
    }

    private void doLogin() {
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (username.isEmpty()) {
            etUsername.setError("请输入用户名");
            etUsername.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("请输入密码");
            etPassword.requestFocus();
            return;
        }

        // 禁用按钮防止重复点击
        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        authManager.login(username, password, (user, error) -> {
            runOnUiThread(() -> {
                btnLogin.setEnabled(true);
                btnLogin.setText("登 录");

                if (user != null) {
                    Toast.makeText(this, "欢迎回来，" + user.displayName, Toast.LENGTH_SHORT).show();
                    goToMain();
                } else {
                    Toast.makeText(this, error != null ? error : "登录失败", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // 双击退出
        super.onBackPressed();
        finishAffinity();
    }
}
