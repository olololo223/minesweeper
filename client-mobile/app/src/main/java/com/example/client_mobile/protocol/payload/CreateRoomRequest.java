package com.example.client_mobile.protocol.payload;

public class CreateRoomRequest {
    public String difficulty;       // EASY / MEDIUM / HARD
    public String roomName;

    public CreateRoomRequest() {}
    public CreateRoomRequest(String difficulty, String roomName) {
        this.difficulty = difficulty;
        this.roomName = roomName;
    }
}
