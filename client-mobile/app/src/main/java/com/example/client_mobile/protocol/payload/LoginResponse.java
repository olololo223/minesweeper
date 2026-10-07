package com.example.client_mobile.protocol.payload;

public class LoginResponse {
    public boolean success;
    public String message;
    public long userId;

    public LoginResponse() {}
    public LoginResponse(boolean success, String message, long userId) {
        this.success = success;
        this.message = message;
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "LoginResponse{success=" + success +
                ", message='" + message + '\'' +
                ", userId=" + userId + '}';
    }
}