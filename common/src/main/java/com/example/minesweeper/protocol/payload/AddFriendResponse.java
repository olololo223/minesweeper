package com.example.minesweeper.protocol.payload;

public class AddFriendResponse {
    public boolean success;
    public String message;

    public AddFriendResponse() {}
    public AddFriendResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
