package com.example.babybedapp.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.babybedapp.db.AppDatabase;
import com.example.babybedapp.db.User;
import com.example.babybedapp.db.UserDao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 认证管理器
 * 处理用户登录、注册、登出等操作
 */
public class AuthManager {
    private static final String TAG = "AuthManager";
    private static final String PREF_NAME = "auth_prefs";
    private static final String KEY_LOGGED_IN_USER_ID = "logged_in_user_id";
    private static final String KEY_LOGIN_TIME = "login_time";
    private static final long SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000; // 24小时

    private static volatile AuthManager INSTANCE;

    private final Context context;
    private final SharedPreferences prefs;
    private final AppDatabase database;
    private final ExecutorService executor;

    private User currentUser;

    private AuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.database = AppDatabase.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static AuthManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AuthManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AuthManager(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 检查是否已登录
     */
    public boolean isLoggedIn() {
        long userId = prefs.getLong(KEY_LOGGED_IN_USER_ID, -1);
        if (userId == -1)
            return false;

        // 检查会话是否过期
        long loginTime = prefs.getLong(KEY_LOGIN_TIME, 0);
        if (System.currentTimeMillis() - loginTime > SESSION_TIMEOUT_MS) {
            logout();
            return false;
        }

        return true;
    }

    /**
     * 获取当前登录用户ID
     */
    public long getCurrentUserId() {
        return prefs.getLong(KEY_LOGGED_IN_USER_ID, -1);
    }

    /**
     * 获取当前用户（异步加载后可用）
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * 加载当前用户（异步）
     */
    public void loadCurrentUser(Callback<User> callback) {
        long userId = getCurrentUserId();
        if (userId == -1) {
            callback.onResult(null, "未登录");
            return;
        }

        executor.execute(() -> {
            try {
                User user = database.userDao().findById(userId);
                if (user != null && user.isActive == 1) {
                    currentUser = user;
                    callback.onResult(user, null);
                } else {
                    logout();
                    callback.onResult(null, "用户不存在或已禁用");
                }
            } catch (Exception e) {
                Log.e(TAG, "加载用户失败: " + e.getMessage());
                callback.onResult(null, e.getMessage());
            }
        });
    }

    /**
     * 登录
     */
    public void login(String username, String password, Callback<User> callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onResult(null, "请输入用户名");
            return;
        }
        if (password == null || password.isEmpty()) {
            callback.onResult(null, "请输入密码");
            return;
        }

        executor.execute(() -> {
            try {
                UserDao userDao = database.userDao();
                User user = userDao.findByUsername(username.trim());

                if (user == null) {
                    callback.onResult(null, "用户不存在");
                    return;
                }

                if (user.isActive != 1) {
                    callback.onResult(null, "账号已被禁用");
                    return;
                }

                // 验证密码
                String hashedPassword = hashPassword(password, user.passwordSalt);
                if (!hashedPassword.equals(user.passwordHash)) {
                    callback.onResult(null, "密码错误");
                    return;
                }

                // 登录成功
                currentUser = user;
                prefs.edit()
                        .putLong(KEY_LOGGED_IN_USER_ID, user.id)
                        .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                        .apply();

                Log.d(TAG, "登录成功: " + user.username);
                callback.onResult(user, null);

            } catch (Exception e) {
                Log.e(TAG, "登录失败: " + e.getMessage());
                callback.onResult(null, "登录失败: " + e.getMessage());
            }
        });
    }

    /**
     * 注册
     */
    public void register(String username, String password, String displayName, String role, String phone,
            Callback<User> callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onResult(null, "请输入用户名");
            return;
        }
        if (password == null || password.length() < 4) {
            callback.onResult(null, "密码至少4位");
            return;
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            callback.onResult(null, "请输入昵称");
            return;
        }

        executor.execute(() -> {
            try {
                UserDao userDao = database.userDao();

                // 检查用户名是否存在
                if (userDao.countByUsername(username.trim()) > 0) {
                    callback.onResult(null, "用户名已存在");
                    return;
                }

                // 创建用户
                User user = new User();
                user.username = username.trim();
                user.displayName = displayName.trim();
                user.role = (role != null && !role.isEmpty()) ? role : "parent";
                user.phone = phone;

                // 生成盐值和哈希密码
                user.passwordSalt = generateSalt();
                user.passwordHash = hashPassword(password, user.passwordSalt);

                // 插入数据库
                long id = userDao.insert(user);
                user.id = id;

                // 自动登录
                currentUser = user;
                prefs.edit()
                        .putLong(KEY_LOGGED_IN_USER_ID, id)
                        .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                        .apply();

                Log.d(TAG, "注册成功: " + user.username);
                callback.onResult(user, null);

            } catch (Exception e) {
                Log.e(TAG, "注册失败: " + e.getMessage());
                callback.onResult(null, "注册失败: " + e.getMessage());
            }
        });
    }

    /**
     * 登出
     */
    public void logout() {
        currentUser = null;
        prefs.edit()
                .remove(KEY_LOGGED_IN_USER_ID)
                .remove(KEY_LOGIN_TIME)
                .apply();
        Log.d(TAG, "已登出");
    }

    /**
     * 修改密码
     */
    public void changePassword(String oldPassword, String newPassword, Callback<Boolean> callback) {
        if (currentUser == null) {
            callback.onResult(false, "未登录");
            return;
        }
        if (newPassword == null || newPassword.length() < 4) {
            callback.onResult(false, "新密码至少4位");
            return;
        }

        executor.execute(() -> {
            try {
                // 验证旧密码
                String oldHash = hashPassword(oldPassword, currentUser.passwordSalt);
                if (!oldHash.equals(currentUser.passwordHash)) {
                    callback.onResult(false, "原密码错误");
                    return;
                }

                // 生成新盐值和哈希
                String newSalt = generateSalt();
                String newHash = hashPassword(newPassword, newSalt);

                // 更新数据库
                database.userDao().updatePassword(
                        currentUser.id,
                        newHash,
                        newSalt,
                        System.currentTimeMillis());

                // 更新内存中的用户
                currentUser.passwordHash = newHash;
                currentUser.passwordSalt = newSalt;

                Log.d(TAG, "密码修改成功");
                callback.onResult(true, null);

            } catch (Exception e) {
                Log.e(TAG, "修改密码失败: " + e.getMessage());
                callback.onResult(false, e.getMessage());
            }
        });
    }

    // ===== 工具方法 =====

    /**
     * 生成盐值
     */
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * 密码哈希（SHA-256 + Salt）
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = password + salt;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 回调接口
     */
    public interface Callback<T> {
        void onResult(T result, String error);
    }
}
