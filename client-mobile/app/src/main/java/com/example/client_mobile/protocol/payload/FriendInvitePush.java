package com.example.client_mobile.protocol.payload;

public class FriendInvitePush {
    public long fromUserId;
    public String fromUsername;
    public long roomId;
    public String roomName;
    public String difficulty;

    public FriendInvitePush() {}

    public FriendInvitePush(long fromUserId, String fromUsername,
                            long roomId, String roomName, String difficulty) {
        this.fromUserId = fromUserId;
        this.fromUsername = fromUsername;
        this.roomId = roomId;
        this.roomName = roomName;
        this.difficulty = difficulty;
    }

    @Override
    public String toString() {
        return "FriendInvitePush{from='" + fromUsername
                + "', room=" + roomId + '}';
    }
}