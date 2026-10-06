package com.example.minesweeper.protocol.payload;

public class FindUserRequest {
    public String username;

    public FindUserRequest() {}
    public FindUserRequest(String username) {
        this.username = username;
    }
}
