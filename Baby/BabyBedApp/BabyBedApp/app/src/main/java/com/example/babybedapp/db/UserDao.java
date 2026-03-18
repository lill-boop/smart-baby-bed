package com.example.babybedapp.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 用户数据访问接口
 */
@Dao
public interface UserDao {

    /**
     * 插入新用户
     */
    @Insert
    long insert(User user);

    /**
     * 更新用户
     */
    @Update
    void update(User user);

    /**
     * 删除用户
     */
    @Delete
    void delete(User user);

    /**
     * 根据用户名查找用户
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    User findByUsername(String username);

    /**
     * 根据ID查找用户
     */
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findById(long id);

    /**
     * 获取所有用户
     */
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    List<User> getAll();

    /**
     * 获取活跃用户
     */
    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY createdAt DESC")
    List<User> getActiveUsers();

    /**
     * 检查用户名是否存在
     */
    @Query("SELECT COUNT(*) FROM users WHERE username = :username")
    int countByUsername(String username);

    /**
     * 更新密码
     */
    @Query("UPDATE users SET passwordHash = :passwordHash, passwordSalt = :passwordSalt, updatedAt = :updatedAt WHERE id = :userId")
    void updatePassword(long userId, String passwordHash, String passwordSalt, long updatedAt);

    /**
     * 更新用户状态
     */
    @Query("UPDATE users SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :userId")
    void updateStatus(long userId, int isActive, long updatedAt);

    /**
     * 更新显示名称
     */
    @Query("UPDATE users SET displayName = :displayName, updatedAt = :updatedAt WHERE id = :userId")
    void updateDisplayName(long userId, String displayName, long updatedAt);
}
