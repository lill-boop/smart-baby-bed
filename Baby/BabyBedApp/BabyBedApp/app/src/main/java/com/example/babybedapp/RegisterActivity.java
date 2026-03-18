package com.example.babybedapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.babybedapp.auth.AuthManager;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 注册页面
 */
public class RegisterActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextInputEditText etDisplayName, etUsername, etPhone, etPassword, etConfirmPassword;
    private RadioGroup rgRole;
    private Button btnRegister;
    private TextView tvGoLogin;

    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authManager = AuthManager.getInstance(this);

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etDisplayName = findViewById(R.id.etDisplayName);
        etUsername = findViewById(R.id.etUsername);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        rgRole = findViewById(R.id.rgRole);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoLogin = findViewById(R.id.tvGoLogin);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        tvGoLogin.setOnClickListener(v -> finish());

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String displayName = getText(etDisplayName);
        String username = getText(etUsername);
        String phone = getText(etPhone);
        String password = getText(etPassword);
        String confirmPassword = getText(etConfirmPassword);

        // 验证
        if (displayName.isEmpty()) {
            etDisplayName.setError("请输入昵称");
            etDisplayName.requestFocus();
            return;
        }

        if (username.isEmpty()) {
            etUsername.setError("请输入用户名");
            etUsername.requestFocus();
            return;
        }

        if (username.length() < 3) {
            etUsername.setError("用户名至少3位");
            etUsername.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("请输入密码");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 4) {
            etPassword.setError("密码至少4位");
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("两次密码不一致");
            etConfirmPassword.requestFocus();
            return;
        }

        // 获取角色
        String role = "parent";
        int checkedId = rgRole.getCheckedRadioButtonId();
        if (checkedId == R.id.rbGuardian) {
            role = "guardian";
        }

        // 禁用按钮
        btnRegister.setEnabled(false);
        btnRegister.setText("注册中...");

        final String finalRole = role;
        authManager.register(username, password, displayName, finalRole, phone, (user, error) -> {
            runOnUiThread(() -> {
                btnRegister.setEnabled(true);
                btnRegister.setText("注 册");

                if (user != null) {
                    Toast.makeText(this, "注册成功，欢迎 " + user.displayName, Toast.LENGTH_SHORT).show();
                    goToMain();
                } else {
                    Toast.makeText(this, error != null ? error : "注册失败", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
