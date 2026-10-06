package com.example.minesweeper.protocol.payload;

public class MyStatsRequest {
    public int limit;   // сколько последних партий вернуть

    public MyStatsRequest() {}

    public MyStatsRequest(int limit) {
        this.limit = limit;
    }
}
