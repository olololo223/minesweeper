package com.example.minesweeper.protocol.payload;

public class LeaderboardRequest {
    public String difficulty;

    public LeaderboardRequest() {}
    public LeaderboardRequest(String difficulty) {
        this.difficulty = difficulty;
    }
}