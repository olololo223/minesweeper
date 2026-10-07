package com.example.client_mobile.protocol.payload;

public class JoinRoomRequest {
    public long roomId;

    public JoinRoomRequest() {}
    public JoinRoomRequest(long roomId) {
        this.roomId = roomId;
    }
}
