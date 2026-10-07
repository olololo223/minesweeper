package com.example.client_mobile.protocol.payload;

public class DeclineFriendResponse {
    public boolean success;
    public String message;

    public DeclineFriendResponse() {}
    public DeclineFriendResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
