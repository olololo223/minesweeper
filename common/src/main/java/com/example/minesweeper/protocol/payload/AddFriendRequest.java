package com.example.minesweeper.protocol.payload;

public class AddFriendRequest {
    public long targetUserId;

    public AddFriendRequest() {}
    public AddFriendRequest(long targetUserId) {
        this.targetUserId = targetUserId;
    }
}
