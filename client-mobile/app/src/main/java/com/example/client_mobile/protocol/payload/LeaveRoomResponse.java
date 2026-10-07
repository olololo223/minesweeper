package com.example.client_mobile.protocol.payload;

public class LeaveRoomResponse {
    public boolean success;
    public String message;

    public LeaveRoomResponse() {}
    public LeaveRoomResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
