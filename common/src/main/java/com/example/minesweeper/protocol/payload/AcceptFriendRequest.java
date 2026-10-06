package com.example.minesweeper.protocol.payload;

public class AcceptFriendRequest {
    public long requesterUserId;    // кто прислал заявку

    public AcceptFriendRequest() {}
    public AcceptFriendRequest(long requesterUserId) {
        this.requesterUserId = requesterUserId;
    }
}
