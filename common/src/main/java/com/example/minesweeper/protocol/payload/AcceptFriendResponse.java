package com.example.minesweeper.protocol.payload;

public class AcceptFriendResponse {
    public boolean success;
    public String message;

    public AcceptFriendResponse() {}
    public AcceptFriendResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
