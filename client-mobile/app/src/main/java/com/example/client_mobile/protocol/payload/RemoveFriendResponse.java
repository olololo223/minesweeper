package com.example.client_mobile.protocol.payload;

public class RemoveFriendResponse {
    public boolean success;
    public String message;

    public RemoveFriendResponse() {}
    public RemoveFriendResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
