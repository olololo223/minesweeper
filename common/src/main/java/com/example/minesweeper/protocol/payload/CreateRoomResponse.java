package com.example.minesweeper.protocol.payload;

public class CreateRoomResponse {
    public boolean success;
    public String message;
    public long roomId;

    public CreateRoomResponse() {}
    public CreateRoomResponse(boolean success, String message, long roomId) {
        this.success = success;
        this.message = message;
        this.roomId = roomId;
    }
}
