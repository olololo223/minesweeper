package com.example.minesweeper.protocol.payload;

public class LoginRequest {
    public String username;
    public String password;

    public LoginRequest() {}
    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}