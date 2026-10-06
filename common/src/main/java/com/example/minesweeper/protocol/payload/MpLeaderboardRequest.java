package com.example.minesweeper.protocol.payload;

public class MpLeaderboardRequest {
    public int limit;

    public MpLeaderboardRequest() {}
    public MpLeaderboardRequest(int limit) {
        this.limit = limit;
    }
}