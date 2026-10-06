package com.example.minesweeper.protocol.payload;

public class JoinRoomRequest {
    public long roomId;

    public JoinRoomRequest() {}
    public JoinRoomRequest(long roomId) {
        this.roomId = roomId;
    }
}
