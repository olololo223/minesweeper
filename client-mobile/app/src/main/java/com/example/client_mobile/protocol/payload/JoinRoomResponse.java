package com.example.client_mobile.protocol.payload;

public class JoinRoomResponse {
    public boolean success;
    public String message;
    public long roomId;

    public JoinRoomResponse() {}
    public JoinRoomResponse(boolean success, String message, long roomId) {
        this.success = success;
        this.message = message;
        this.roomId = roomId;
    }
}
