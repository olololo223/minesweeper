package com.example.client_mobile.protocol.payload;

public class ChangePasswordResponse {
    public boolean success;
    public String message;

    public ChangePasswordResponse() {}

    public ChangePasswordResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    @Override
    public String toString() {
        return "ChangePasswordResponse{success=" + success
                + ", message='" + message + "'}";
    }
}