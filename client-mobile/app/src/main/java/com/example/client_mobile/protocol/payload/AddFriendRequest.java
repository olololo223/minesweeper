package com.example.client_mobile.protocol.payload;

public class AddFriendRequest {
    public long targetUserId;

    public AddFriendRequest() {}
    public AddFriendRequest(long targetUserId) {
        this.targetUserId = targetUserId;
    }
}
