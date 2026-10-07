package com.example.client_mobile.protocol.payload;

public class RoomFinishedPush {
    public long roomId;
    public boolean won;                    // true — все не-мины открыты
    public String message;

    public RoomFinishedPush() {}
    public RoomFinishedPush(long roomId, boolean won, String message) {
        this.roomId = roomId;
        this.won = won;
        this.message = message;
    }

    @Override
    public String toString() {
        return "RoomFinishedPush{room=" + roomId + ", won=" + won
                + ", message='" + message + "'}";
    }
}
