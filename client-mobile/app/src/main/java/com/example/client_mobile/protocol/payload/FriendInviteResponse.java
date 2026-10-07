package com.example.client_mobile.protocol.payload;

public class FriendInviteResponse {
    public boolean success;
    public String message;

    public FriendInviteResponse() {}
    public FriendInviteResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    @Override
    public String toString() {
        return "FriendInviteResponse{success=" + success
                + ", message='" + message + "'}";
    }
}