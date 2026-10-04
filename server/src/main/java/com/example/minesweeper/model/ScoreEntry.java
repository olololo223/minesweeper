package com.example.minesweeper.model;

public class ScoreEntry {
    public final String username;
    public final int bestTimeSeconds;

    public ScoreEntry(String username, int bestTimeSeconds) {
        this.username = username;
        this.bestTimeSeconds = bestTimeSeconds;
    }
}