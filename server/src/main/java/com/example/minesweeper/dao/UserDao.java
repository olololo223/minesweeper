package com.example.minesweeper.dao;

import com.example.minesweeper.db.DataSourceProvider;
import com.example.minesweeper.model.User;

import java.sql.*;

public class UserDao {

    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, role, password_hash FROM users WHERE username = ?";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User(rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("role"));
                    u.passwordHash = rs.getString("password_hash");
                    return u;
                }
            }
        }
        return null;
    }

    public User create(String username, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users(username, password_hash) VALUES (?, ?)";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    User u = new User(keys.getLong(1), username, "USER");
                    u.passwordHash = passwordHash;
                    return u;
                }
            }
        }
        throw new SQLException("Не удалось создать пользователя");
    }

    /** Установить пароль существующему пользователю (миграция старой учётки). */
    public void setPasswordHash(long userId, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }
}