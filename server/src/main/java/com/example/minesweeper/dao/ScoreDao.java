package com.example.minesweeper.dao;

import com.example.minesweeper.db.DataSourceProvider;
import com.example.minesweeper.protocol.payload.MyStatsResponse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ScoreDao {

    /** Сохранить партию. */
    public void saveRecord(long userId, String difficulty, String mode,
                           int durationSeconds, boolean win) throws SQLException {
        String sql = "INSERT INTO game_records(user_id, difficulty, mode, " +
                "duration_seconds, win) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, difficulty);
            ps.setString(3, mode);
            ps.setInt(4, durationSeconds);
            ps.setBoolean(5, win);
            ps.executeUpdate();
        }
    }

    /** Обновить лучший результат, если он лучше текущего. */
    public void updateBestIfBetter(long userId, String difficulty, String mode,
                                   int durationSeconds) throws SQLException {
        String sql =
                "INSERT INTO leaderboard(user_id, difficulty, mode, best_time_seconds) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "best_time_seconds = LEAST(best_time_seconds, VALUES(best_time_seconds))";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, difficulty);
            ps.setString(3, mode);
            ps.setInt(4, durationSeconds);
            ps.executeUpdate();
        }
    }

    /** Сводка по пользователю в разрезе (сложность, режим). */
    public List<MyStatsResponse.DifficultyStats> statsByDifficulty(long userId)
            throws SQLException {
        String sql =
            "SELECT difficulty, mode, " +
            "       COUNT(*) AS total_games, " +
            "       SUM(CASE WHEN win THEN 1 ELSE 0 END) AS wins, " +
            "       MIN(CASE WHEN win THEN duration_seconds END) AS best_time " +
            "FROM game_records " +
            "WHERE user_id = ? " +
            "GROUP BY difficulty, mode " +
            "ORDER BY FIELD(mode, 'CLASSIC', 'TIMED'), " +
            "         FIELD(difficulty, 'EASY', 'MEDIUM', 'HARD')";
        List<MyStatsResponse.DifficultyStats> out = new ArrayList<>();
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int bestRaw = rs.getInt("best_time");
                    Integer best = rs.wasNull() ? null : bestRaw;
                    out.add(new MyStatsResponse.DifficultyStats(
                            rs.getString("difficulty"),
                            rs.getString("mode"),
                            rs.getInt("total_games"),
                            rs.getInt("wins"),
                            best));
                }
            }
        }
        return out;
    }

    /** Последние N партий пользователя. */
    public List<MyStatsResponse.GameEntry> recentGames(long userId, int limit)
            throws SQLException {
        String sql =
            "SELECT difficulty, mode, duration_seconds, win, played_at " +
            "FROM game_records " +
            "WHERE user_id = ? " +
            "ORDER BY played_at DESC " +
            "LIMIT ?";
        List<MyStatsResponse.GameEntry> out = new ArrayList<>();
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MyStatsResponse.GameEntry(
                            rs.getString("difficulty"),
                            rs.getString("mode"),
                            rs.getInt("duration_seconds"),
                            rs.getBoolean("win"),
                            rs.getTimestamp("played_at").toLocalDateTime().toString()));
                }
            }
        }
        return out;
    }
}