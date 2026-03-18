package com.example.babybedapp.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 用户实体类
 * 存储用户账号信息
 */
@Entity(tableName = "users", indices = { @Index(value = "username", unique = true) })
public class User {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * 用户名（唯一）
     */
    public String username;

    /**
     * 密码哈希（SHA-256 + Salt）
     */
    public String passwordHash;

    /**
     * 密码盐值
     */
    public String passwordSalt;

    /**
     * 显示名称（昵称）
     */
    public String displayName;

    /**
     * 角色: parent, guardian, admin
     */
    public String role;

    /**
     * 手机号（可选）
     */
    public String phone;

    /**
     * 邮箱（可选）
     */
    public String email;

    /**
     * 头像URI
     */
    public String avatarUri;

    /**
     * 是否启用 (1=启用, 0=禁用)
     */
    public int isActive = 1;

    /**
     * 创建时间戳
     */
    public long createdAt;

    /**
     * 更新时间戳
     */
    public long updatedAt;

    // 构造函数
    public User() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.role = "parent";
        this.isActive = 1;
    }

    /**
     * 创建新用户
     */
    public static User create(String username, String displayName, String role) {
        User user = new User();
        user.username = username;
        user.displayName = displayName;
        user.role = role != null ? role : "parent";
        return user;
    }

    /**
     * 获取角色显示名称
     */
    public String getRoleDisplayName() {
        if ("parent".equals(role))
            return "父母";
        if ("guardian".equals(role))
            return "监护人";
        if ("admin".equals(role))
            return "管理员";
        return role;
    }
}
