package com.example.minesweeper.protocol.payload;

public class MpStatsResponse {
    public boolean success;
    public String message;

    public long totalScore;
    public int totalGames;
    public int totalWins;
    public int totalRevealed;
    public double avgRevealed;
    public int bestScore;

    public MpStatsResponse() {}

    @Override
    public String toString() {
        return "MpStatsResponse{games=" + totalGames
                + ", wins=" + totalWins
                + ", score=" + totalScore + '}';
    }
}