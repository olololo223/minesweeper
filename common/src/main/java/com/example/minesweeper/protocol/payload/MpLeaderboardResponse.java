package com.example.minesweeper.protocol.payload;

import java.util.List;

public class MpLeaderboardResponse {
    public static class Entry {
        public String username;
        public long totalScore;
        public int totalGames;
        public int totalWins;

        public Entry() {}

        public Entry(String username, long totalScore,
                     int totalGames, int totalWins) {
            this.username = username;
            this.totalScore = totalScore;
            this.totalGames = totalGames;
            this.totalWins = totalWins;
        }
    }

    public boolean success;
    public String message;
    public List<Entry> entries;

    public MpLeaderboardResponse() {}

    public MpLeaderboardResponse(boolean success, String message,
                                 List<Entry> entries) {
        this.success = success;
        this.message = message;
        this.entries = entries;
    }
}