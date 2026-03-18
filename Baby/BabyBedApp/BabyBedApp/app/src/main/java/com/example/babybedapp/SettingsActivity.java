package com.example.babybedapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.babybedapp.auth.AuthManager;
import com.example.babybedapp.db.User;

/**
 * 设置页面
 */
public class SettingsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private ImageView ivAvatar;
    private TextView tvDisplayName, tvRole, tvPhone, tvDeviceCount;
    private LinearLayout layoutChangePassword, layoutBindPhone, layoutMyDevices, layoutFamilyMembers;
    private Button btnLogout;

    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        authManager = AuthManager.getInstance(this);

        initViews();
        setupListeners();
        loadUserInfo();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvDisplayName = findViewById(R.id.tvDisplayName);
        tvRole = findViewById(R.id.tvRole);
        tvPhone = findViewById(R.id.tvPhone);
        tvDeviceCount = findViewById(R.id.tvDeviceCount);
        layoutChangePassword = findViewById(R.id.layoutChangePassword);
        layoutBindPhone = findViewById(R.id.layoutBindPhone);
        layoutMyDevices = findViewById(R.id.layoutMyDevices);
        layoutFamilyMembers = findViewById(R.id.layoutFamilyMembers);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        layoutChangePassword.setOnClickListener(v -> {
            // TODO: 实现修改密码对话框
            Toast.makeText(this, "修改密码功能开发中", Toast.LENGTH_SHORT).show();
        });

        layoutBindPhone.setOnClickListener(v -> {
            Toast.makeText(this, "绑定手机功能开发中", Toast.LENGTH_SHORT).show();
        });

        layoutMyDevices.setOnClickListener(v -> {
            Toast.makeText(this, "设备管理功能开发中", Toast.LENGTH_SHORT).show();
        });

        layoutFamilyMembers.setOnClickListener(v -> {
            Toast.makeText(this, "家庭成员功能开发中", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    private void loadUserInfo() {
        authManager.loadCurrentUser((user, error) -> {
            runOnUiThread(() -> {
                if (user != null) {
                    updateUI(user);
                } else {
                    // 未登录，跳转登录页
                    goToLogin();
                }
            });
        });
    }

    private void updateUI(User user) {
        tvDisplayName.setText(user.displayName != null ? user.displayName : user.username);
        tvRole.setText(user.getRoleDisplayName());

        if (user.phone != null && !user.phone.isEmpty()) {
            // 隐藏中间4位
            String masked = user.phone.length() > 7
                    ? user.phone.substring(0, 3) + "****" + user.phone.substring(7)
                    : user.phone;
            tvPhone.setText(masked);
        } else {
            tvPhone.setText("未绑定");
        }

        // 设备数量 - 暂时固定
        tvDeviceCount.setText("1 台设备");
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setPositiveButton("退出", (dialog, which) -> {
                    authManager.logout();
                    goToLogin();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
