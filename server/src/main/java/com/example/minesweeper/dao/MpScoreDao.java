package com.example.minesweeper.dao;

import com.example.minesweeper.db.DataSourceProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MpScoreDao {

    /** Сохранить результат партии мультиплеера. */
    public void saveRecord(long userId, long roomId, String difficulty,
                           int revealedCells, boolean exploded,
                           boolean win, int score) throws SQLException {
        String sql =
                "INSERT INTO mp_records(user_id, room_id, difficulty, " +
                        "revealed_cells, exploded, win, score) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, roomId);
            ps.setString(3, difficulty);
            ps.setInt(4, revealedCells);
            ps.setBoolean(5, exploded);
            ps.setBoolean(6, win);
            ps.setInt(7, score);
            ps.executeUpdate();
        }
    }

    /** Обновить суммарный рейтинг игрока. */
    public void updateLeaderboard(long userId, int score, boolean win)
            throws SQLException {
        String sql =
                "INSERT INTO mp_leaderboard(user_id, total_score, total_games, total_wins) " +
                        "VALUES (?, ?, 1, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  total_score = total_score + VALUES(total_score), " +
                        "  total_games = total_games + 1, " +
                        "  total_wins  = total_wins + VALUES(total_wins)";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, score);
            ps.setInt(3, win ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /** Топ игроков по суммарным очкам. */
    public List<Object[]> topScores(int limit) throws SQLException {
        String sql =
                "SELECT u.username, l.total_score, l.total_games, l.total_wins " +
                        "FROM mp_leaderboard l JOIN users u ON u.id = l.user_id " +
                        "ORDER BY l.total_score DESC " +
                        "LIMIT ?";
        List<Object[]> out = new ArrayList<>();
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Object[]{
                            rs.getString("username"),
                            rs.getLong("total_score"),
                            rs.getInt("total_games"),
                            rs.getInt("total_wins")
                    });
                }
            }
        }
        return out;
    }

    /** Личная статистика мультиплеера. */
    public static class PlayerMpStats {
        public long totalScore;
        public int totalGames;
        public int totalWins;
        public int totalRevealed;
        public double avgRevealed;
        public int bestScore;

        public PlayerMpStats() {}
    }

    public PlayerMpStats statsForUser(long userId) throws SQLException {
        // Общая сводка
        PlayerMpStats s = new PlayerMpStats();
        String sql =
                "SELECT COUNT(*) AS games, " +
                        "       COALESCE(SUM(revealed_cells), 0) AS revealed, " +
                        "       COALESCE(SUM(CASE WHEN win THEN 1 ELSE 0 END), 0) AS wins, " +
                        "       COALESCE(MAX(score), 0) AS best " +
                        "FROM mp_records WHERE user_id = ?";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.totalGames = rs.getInt("games");
                    s.totalRevealed = rs.getInt("revealed");
                    s.totalWins = rs.getInt("wins");
                    s.bestScore = rs.getInt("best");
                    s.avgRevealed = s.totalGames == 0 ? 0.0
                            : (double) s.totalRevealed / s.totalGames;
                }
            }
        }

        // Суммарные очки из рейтинга (быстрее, чем SUM по mp_records)
        String sqlLb = "SELECT total_score FROM mp_leaderboard WHERE user_id = ?";
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sqlLb)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) s.totalScore = rs.getLong("total_score");
            }
        }
        return s;
    }
}