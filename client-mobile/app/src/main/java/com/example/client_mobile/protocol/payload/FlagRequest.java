package com.example.client_mobile.protocol.payload;

public class FlagRequest {
    public int row;
    public int col;

    public FlagRequest() {}
    public FlagRequest(int row, int col) {
        this.row = row;
        this.col = col;
    }
}
