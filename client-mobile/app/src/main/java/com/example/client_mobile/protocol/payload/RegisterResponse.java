package com.example.client_mobile.protocol.payload;

public class RegisterResponse {
    public boolean success;
    public String message;

    public RegisterResponse() {}
    public RegisterResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    @Override
    public String toString() {
        return "RegisterResponse{success=" + success + ", message='" + message + "'}";
    }
}