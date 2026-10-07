package com.example.client_mobile.protocol.payload;

public class LeaderboardRequest {
    public String difficulty;
    public String mode;            // CLASSIC / TIMED

    public LeaderboardRequest() {}
    public LeaderboardRequest(String difficulty, String mode) {
        this.difficulty = difficulty;
        this.mode = mode;
    }
}
