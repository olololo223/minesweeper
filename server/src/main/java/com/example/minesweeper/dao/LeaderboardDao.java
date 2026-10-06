package com.example.minesweeper.dao;

import com.example.minesweeper.db.DataSourceProvider;
import com.example.minesweeper.model.ScoreEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardDao {

    public List<ScoreEntry> top(String difficulty, String mode, int limit)
            throws SQLException {
        String sql =
                "SELECT u.username, l.best_time_seconds " +
                        "FROM leaderboard l JOIN users u ON u.id = l.user_id " +
                        "WHERE l.difficulty = ? AND l.mode = ? " +
                        "ORDER BY l.best_time_seconds ASC " +
                        "LIMIT ?";
        List<ScoreEntry> list = new ArrayList<>();
        try (Connection c = DataSourceProvider.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, difficulty);
            ps.setString(2, mode);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ScoreEntry(
                            rs.getString("username"),
                            rs.getInt("best_time_seconds")));
                }
            }
        }
        return list;
    }
}