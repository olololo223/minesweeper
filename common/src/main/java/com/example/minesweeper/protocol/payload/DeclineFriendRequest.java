package com.example.minesweeper.protocol.payload;

public class DeclineFriendRequest {
    public long requesterUserId;

    public DeclineFriendRequest() {}
    public DeclineFriendRequest(long requesterUserId) {
        this.requesterUserId = requesterUserId;
    }
}
