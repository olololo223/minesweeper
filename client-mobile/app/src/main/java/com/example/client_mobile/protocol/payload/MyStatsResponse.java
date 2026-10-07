package com.example.client_mobile.protocol.payload;

import java.util.List;

public class MyStatsResponse {

    /** Сводка по одной паре (сложность, режим). */
    public static class DifficultyStats {
        public String difficulty;
        public String mode;            // CLASSIC / TIMED
        public int totalGames;
        public int wins;
        public Integer bestTimeSeconds;   // null, если побед не было

        public DifficultyStats() {}

        public DifficultyStats(String difficulty, String mode,
                               int totalGames, int wins,
                               Integer bestTimeSeconds) {
            this.difficulty = difficulty;
            this.mode = mode;
            this.totalGames = totalGames;
            this.wins = wins;
            this.bestTimeSeconds = bestTimeSeconds;
        }
    }

    /** Одна партия (для таблицы и графика). */
    public static class GameEntry {
        public String difficulty;
        public String mode;            // CLASSIC / TIMED
        public int durationSeconds;
        public boolean win;
        public String playedAt;      // строка, чтобы не тащить java.time через Jackson

        public GameEntry() {}

        public GameEntry(String difficulty, String mode,
                         int durationSeconds, boolean win, String playedAt) {
            this.difficulty = difficulty;
            this.mode = mode;
            this.durationSeconds = durationSeconds;
            this.win = win;
            this.playedAt = playedAt;
        }
    }

    /** Итоги по одному режиму (CLASSIC / TIMED). */
    public static class ModeTotals {
        public String mode;         // CLASSIC / TIMED
        public int totalGames;
        public int totalWins;

        public ModeTotals() {}

        public ModeTotals(String mode, int totalGames, int totalWins) {
            this.mode = mode;
            this.totalGames = totalGames;
            this.totalWins = totalWins;
        }
    }

    public boolean success;
    public String message;

    public int totalGames;
    public int totalWins;

    public List<DifficultyStats> byDifficulty;
    public List<GameEntry> recentGames;
    public List<ModeTotals> modeTotals;   // готовые итоги по режимам

    public MyStatsResponse() {}

    @Override
    public String toString() {
        return "MyStatsResponse{success=" + success
                + ", totalGames=" + totalGames
                + ", totalWins=" + totalWins
                + ", modeTotals=" + modeTotals + '}';
    }
}
