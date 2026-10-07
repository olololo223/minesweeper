package com.example.client_mobile.protocol.payload;

public class StartRoomResponse {
    public boolean success;
    public String message;

    public StartRoomResponse() {}
    public StartRoomResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
