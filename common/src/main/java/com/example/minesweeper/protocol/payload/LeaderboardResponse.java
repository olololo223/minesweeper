package com.example.minesweeper.protocol.payload;

import java.util.List;

public class LeaderboardResponse {
    public static class Entry {
        public String username;
        public int bestTimeSeconds;

        public Entry() {}
        public Entry(String username, int bestTimeSeconds) {
            this.username = username;
            this.bestTimeSeconds = bestTimeSeconds;
        }

        @Override
        public String toString() {
            return username + " (" + bestTimeSeconds + " сек.)";
        }
    }

    public String difficulty;
    public List<Entry> entries;

    public LeaderboardResponse() {}
    public LeaderboardResponse(String difficulty, List<Entry> entries) {
        this.difficulty = difficulty;
        this.entries = entries;
    }

    @Override
    public String toString() {
        return "LeaderboardResponse{difficulty='" + difficulty +
                "', entries=" + entries + '}';
    }
}