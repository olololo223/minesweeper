package com.example.minesweeper.dao;

import com.example.minesweeper.db.DataSourceProvider;

import java.sql.*;

public class ScoreDao {

    /** Сохранить партию. */
    public void saveRecord(long userId, String difficulty,
                           int durationSeconds, boolean win) throws SQLException {
        String sql = "INSERT INTO game_records(user_id, difficulty, duration_seconds, win) " +
                "VALUES (?, ?, ?, ?)";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, difficulty);
            ps.setInt(3, durationSeconds);
            ps.setBoolean(4, win);
            ps.executeUpdate();
        }
    }

    /** Обновить лучший результат, если он лучше текущего. */
    public void updateBestIfBetter(long userId, String difficulty,
                                   int durationSeconds) throws SQLException {
        String sql =
                "INSERT INTO leaderboard(user_id, difficulty, best_time_seconds) " +
                        "VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "best_time_seconds = LEAST(best_time_seconds, VALUES(best_time_seconds))";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, difficulty);
            ps.setInt(3, durationSeconds);
            ps.executeUpdate();
        }
    }
}