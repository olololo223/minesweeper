package com.example.minesweeper.protocol.payload;

public class SubmitScoreRequest {
    public String difficulty;      // EASY / MEDIUM / HARD
    public int durationSeconds;
    public boolean win;

    public SubmitScoreRequest() {}
    public SubmitScoreRequest(String difficulty,
                              int durationSeconds, boolean win) {
        this.difficulty = difficulty;
        this.durationSeconds = durationSeconds;
        this.win = win;
    }
}