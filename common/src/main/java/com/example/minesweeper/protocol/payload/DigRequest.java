package com.example.minesweeper.protocol.payload;

public class DigRequest {
    public int row;
    public int col;

    public DigRequest() {}
    public DigRequest(int row, int col) {
        this.row = row;
        this.col = col;
    }
}
