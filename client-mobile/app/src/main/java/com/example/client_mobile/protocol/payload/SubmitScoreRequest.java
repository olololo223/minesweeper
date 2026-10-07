package com.example.client_mobile.protocol.payload;

public class SubmitScoreRequest {
    public String difficulty;      // EASY / MEDIUM / HARD
    public String mode;            // CLASSIC / TIMED
    public int durationSeconds;
    public boolean win;

    public SubmitScoreRequest() {}
    public SubmitScoreRequest(String difficulty, String mode,
                              int durationSeconds, boolean win) {
        this.difficulty = difficulty;
        this.mode = mode;
        this.durationSeconds = durationSeconds;
        this.win = win;
    }
}
