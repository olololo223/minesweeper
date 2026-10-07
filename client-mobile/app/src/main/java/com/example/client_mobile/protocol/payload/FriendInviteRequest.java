package com.example.client_mobile.protocol.payload;

public class FriendInviteRequest {
    public long friendUserId;
    public long roomId;

    public FriendInviteRequest() {}
    public FriendInviteRequest(long friendUserId, long roomId) {
        this.friendUserId = friendUserId;
        this.roomId = roomId;
    }
}