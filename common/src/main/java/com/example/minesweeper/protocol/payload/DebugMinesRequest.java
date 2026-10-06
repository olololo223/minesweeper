package com.example.minesweeper.protocol.payload;

public class DebugMinesRequest {
    public long roomId;

    public DebugMinesRequest() {}
    public DebugMinesRequest(long roomId) {
        this.roomId = roomId;
    }
}
