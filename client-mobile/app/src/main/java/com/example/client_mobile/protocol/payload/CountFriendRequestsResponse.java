package com.example.client_mobile.protocol.payload;

public class CountFriendRequestsResponse {
    public boolean success;
    public String message;
    public int count;              // сколько входящих заявок

    public CountFriendRequestsResponse() {}
    public CountFriendRequestsResponse(boolean success, String message, int count) {
        this.success = success;
        this.message = message;
        this.count = count;
    }
}
