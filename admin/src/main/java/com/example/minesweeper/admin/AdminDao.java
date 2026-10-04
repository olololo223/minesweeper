package com.example.minesweeper.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class AdminDao {

    private final JdbcTemplate jdbc;

    public AdminDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ===== БЕЛЫЕ СПИСКИ для сортировки (защита от SQL-инъекций) =====

    private static final Set<String> USER_SORT_COLS =
            Set.of("id", "username", "role", "created_at");

    private static final Set<String> RECORD_SORT_COLS =
            Set.of("id", "username", "difficulty", "duration_seconds", "win", "played_at");

    private static final Set<String> LB_SORT_COLS =
            Set.of("id", "username", "difficulty", "best_time_seconds", "updated_at");

    /** Возвращает безопасное имя колонки: либо из белого списка, либо дефолт. */
    private static String safeCol(String col, Set<String> allowed, String fallback) {
        return (col != null && allowed.contains(col)) ? col : fallback;
    }

    /** Направление сортировки — только ASC или DESC. */
    private static String safeDir(String dir) {
        return "desc".equalsIgnoreCase(dir) ? "DESC" : "ASC";
    }

    // ===== ПОЛЬЗОВАТЕЛИ =====

    public List<Map<String, Object>> listUsers(String sort, String dir) {
        String col = safeCol(sort, USER_SORT_COLS, "id");
        String order = safeDir(dir);
        return jdbc.queryForList(
                "SELECT id, username, role, created_at FROM users " +
                        "ORDER BY " + col + " " + order);
    }

    public void deleteUser(long id) {
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    public void updateUserRole(long id, String role) {
        jdbc.update("UPDATE users SET role = ? WHERE id = ?", role, id);
    }

    // ===== ИГРОВЫЕ ЗАПИСИ =====

    public List<Map<String, Object>> listRecords(int limit, String sort, String dir) {
        String col = safeCol(sort, RECORD_SORT_COLS, "played_at");
        // Для сортировки по username нужен префикс таблицы
        String orderCol = "username".equals(col) ? "u.username" : "g." + col;
        String order = safeDir(dir);
        return jdbc.queryForList(
                "SELECT g.id, u.username, g.difficulty, g.duration_seconds, " +
                        "       g.win, g.played_at " +
                        "FROM game_records g JOIN users u ON u.id = g.user_id " +
                        "ORDER BY " + orderCol + " " + order + " " +
                        "LIMIT ?", limit);
    }

    public List<Map<String, Object>> listRecordsByUser(long userId) {
        return jdbc.queryForList(
                "SELECT id, difficulty, duration_seconds, win, played_at " +
                        "FROM game_records WHERE user_id = ? " +
                        "ORDER BY played_at DESC", userId);
    }

    public void deleteRecord(long id) {
        jdbc.update("DELETE FROM game_records WHERE id = ?", id);
    }

    // ===== РЕЙТИНГ =====

    public List<Map<String, Object>> listLeaderboard(String difficulty,
                                                     String sort, String dir) {
        String col = safeCol(sort, LB_SORT_COLS, "best_time_seconds");
        String orderCol = "username".equals(col) ? "u.username" : "l." + col;
        // По умолчанию рейтинг логично сортировать по времени возрастанию
        String order = safeDir(dir == null ? "asc" : dir);
        return jdbc.queryForList(
                "SELECT l.id, u.username, l.difficulty, l.best_time_seconds, " +
                        "       l.updated_at " +
                        "FROM leaderboard l JOIN users u ON u.id = l.user_id " +
                        "WHERE l.difficulty = ? " +
                        "ORDER BY " + orderCol + " " + order, difficulty);
    }

    public void updateLeaderboardTime(long id, int seconds) {
        jdbc.update("UPDATE leaderboard SET best_time_seconds = ? WHERE id = ?",
                seconds, id);
    }

    public void deleteLeaderboardEntry(long id) {
        jdbc.update("DELETE FROM leaderboard WHERE id = ?", id);
    }

    public void clearLeaderboard(String difficulty) {
        jdbc.update("DELETE FROM leaderboard WHERE difficulty = ?", difficulty);
    }

    // ===== СТАТИСТИКА (без изменений) =====

    public Map<String, Object> stats() {
        Map<String, Object> s = new java.util.HashMap<>();
        s.put("users", jdbc.queryForObject(
                "SELECT COUNT(*) FROM users", Long.class));
        s.put("records", jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_records", Long.class));
        s.put("wins", jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_records WHERE win = TRUE", Long.class));
        return s;
    }

    public List<Map<String, Object>> topByDifficulty(String difficulty) {
        return jdbc.queryForList(
                "SELECT u.username, l.best_time_seconds " +
                        "FROM leaderboard l JOIN users u ON u.id = l.user_id " +
                        "WHERE l.difficulty = ? " +
                        "ORDER BY l.best_time_seconds ASC LIMIT 5", difficulty);
    }

    public void logAction(String action, String target, String details) {
        jdbc.update("INSERT INTO admin_actions(action, target, details) VALUES (?, ?, ?)",
                action, target, details);
    }

    public List<Map<String, Object>> listActions(int limit) {
        return jdbc.queryForList(
                "SELECT id, action, target, details, created_at " +
                        "FROM admin_actions ORDER BY created_at DESC LIMIT ?", limit);
    }
}