package com.example.minesweeper.protocol.payload;

public class FriendPush {
    public String event;             // NEW_REQUEST / ACCEPTED
    public long fromUserId;
    public String fromUsername;
    public int incomingCount;        // актуальный счётчик входящих заявок

    public FriendPush() {}

    public FriendPush(String event, long fromUserId, String fromUsername,
                      int incomingCount) {
        this.event = event;
        this.fromUserId = fromUserId;
        this.fromUsername = fromUsername;
        this.incomingCount = incomingCount;
    }

    @Override
    public String toString() {
        return "FriendPush{event='" + event + "', from='" + fromUsername
                + "', incoming=" + incomingCount + '}';
    }
}
