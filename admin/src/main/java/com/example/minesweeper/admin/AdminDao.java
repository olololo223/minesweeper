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
            Set.of("id", "username", "difficulty", "mode",
                    "duration_seconds", "win", "played_at");

    private static final Set<String> LB_SORT_COLS =
            Set.of("id", "username", "difficulty", "mode",
                    "best_time_seconds", "updated_at");

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

    /** mode = null / "ALL" — без фильтра по режиму. */
    public List<Map<String, Object>> listRecords(int limit, String mode,
                                                 String sort, String dir) {
        String col = safeCol(sort, RECORD_SORT_COLS, "played_at");
        // Для сортировки по username нужен префикс таблицы
        String orderCol = "username".equals(col) ? "u.username" : "g." + col;
        String order = safeDir(dir);

        StringBuilder sql = new StringBuilder(
                "SELECT g.id, u.username, g.difficulty, g.mode, g.duration_seconds, " +
                        "       g.win, g.played_at " +
                        "FROM game_records g JOIN users u ON u.id = g.user_id ");

        boolean filterMode = isModeFilter(mode);
        if (filterMode) sql.append("WHERE g.mode = ? ");

        sql.append("ORDER BY ").append(orderCol).append(" ").append(order)
                .append(" LIMIT ?");

        if (filterMode) {
            return jdbc.queryForList(sql.toString(), mode, limit);
        }
        return jdbc.queryForList(sql.toString(), limit);
    }

    public List<Map<String, Object>> listRecordsByUser(long userId) {
        return jdbc.queryForList(
                "SELECT id, difficulty, mode, duration_seconds, win, played_at " +
                        "FROM game_records WHERE user_id = ? " +
                        "ORDER BY played_at DESC", userId);
    }

    public void deleteRecord(long id) {
        jdbc.update("DELETE FROM game_records WHERE id = ?", id);
    }

    // ===== РЕЙТИНГ =====

    /** mode = null / "ALL" — без фильтра по режиму. */
    public List<Map<String, Object>> listLeaderboard(String difficulty, String mode,
                                                     String sort, String dir) {
        String col = safeCol(sort, LB_SORT_COLS, "best_time_seconds");
        String orderCol = "username".equals(col) ? "u.username" : "l." + col;
        // По умолчанию рейтинг логично сортировать по времени по возрастанию
        String order = safeDir(dir == null ? "asc" : dir);

        boolean filterMode = isModeFilter(mode);
        String sql =
                "SELECT l.id, u.username, l.difficulty, l.mode, l.best_time_seconds, " +
                        "       l.updated_at " +
                        "FROM leaderboard l JOIN users u ON u.id = l.user_id " +
                        "WHERE l.difficulty = ? " +
                        (filterMode ? "AND l.mode = ? " : "") +
                        "ORDER BY " + orderCol + " " + order;

        if (filterMode) {
            return jdbc.queryForList(sql, difficulty, mode);
        }
        return jdbc.queryForList(sql, difficulty);
    }

    public void updateLeaderboardTime(long id, int seconds) {
        jdbc.update("UPDATE leaderboard SET best_time_seconds = ? WHERE id = ?",
                seconds, id);
    }

    public void deleteLeaderboardEntry(long id) {
        jdbc.update("DELETE FROM leaderboard WHERE id = ?", id);
    }

    /** mode = null / "ALL" — чистим все режимы этой сложности. */
    public void clearLeaderboard(String difficulty, String mode) {
        if (isModeFilter(mode)) {
            jdbc.update("DELETE FROM leaderboard WHERE difficulty = ? AND mode = ?",
                    difficulty, mode);
        } else {
            jdbc.update("DELETE FROM leaderboard WHERE difficulty = ?", difficulty);
        }
    }

    /** Фильтр по режиму задан и не равен "ALL". */
    private static boolean isModeFilter(String mode) {
        return mode != null && !mode.isBlank() && !"ALL".equals(mode);
    }

    // ===== СТАТИСТИКА =====

    public Map<String, Object> stats() {
        Map<String, Object> s = new java.util.HashMap<>();
        s.put("users", jdbc.queryForObject(
                "SELECT COUNT(*) FROM users", Long.class));
        s.put("records", jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_records", Long.class));
        s.put("wins", jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_records WHERE win = TRUE", Long.class));
        s.put("recordsClassic", jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_records WHERE mode = 'CLASSIC'", Long.class));
        s.put("recordsTimed", jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_records WHERE mode = 'TIMED'", Long.class));
        return s;
    }

    /** Топ-5 по одной паре (сложность, режим) — режимы не смешиваются. */
    public List<Map<String, Object>> topByDifficulty(String difficulty, String mode) {
        return jdbc.queryForList(
                "SELECT u.username, l.mode, l.best_time_seconds " +
                        "FROM leaderboard l JOIN users u ON u.id = l.user_id " +
                        "WHERE l.difficulty = ? AND l.mode = ? " +
                        "ORDER BY l.best_time_seconds ASC " +
                        "LIMIT 5", difficulty, mode);
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

    public List<Map<String, Object>> listMpRecords(int limit) {
        return jdbc.queryForList(
                "SELECT m.id, u.username, m.room_id, m.difficulty, " +
                        "       m.revealed_cells, m.exploded, m.win, m.score, m.played_at " +
                        "FROM mp_records m JOIN users u ON u.id = m.user_id " +
                        "ORDER BY m.played_at DESC LIMIT ?", limit);
    }

    public List<Map<String, Object>> listMpLeaderboard() {
        return jdbc.queryForList(
                "SELECT u.username, l.total_score, l.total_games, l.total_wins " +
                        "FROM mp_leaderboard l JOIN users u ON u.id = l.user_id " +
                        "ORDER BY l.total_score DESC");
    }

    public void deleteMpRecord(long id) {
        jdbc.update("DELETE FROM mp_records WHERE id = ?", id);
    }
}