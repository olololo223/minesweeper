package com.example.minesweeper.protocol.payload;

import java.util.List;

public class GetFriendsResponse {

    /** Один элемент списка — друг или заявка. */
    public static class FriendInfo {
        public long userId;
        public String username;
        public String status;        // ACCEPTED / PENDING_IN / PENDING_OUT
        public String since;         // created_at в ISO

        public FriendInfo() {}

        public FriendInfo(long userId, String username,
                          String status, String since) {
            this.userId = userId;
            this.username = username;
            this.status = status;
            this.since = since;
        }
    }

    public boolean success;
    public String message;
    public List<FriendInfo> friends;

    public GetFriendsResponse() {}

    @Override
    public String toString() {
        return "GetFriendsResponse{count=" +
                (friends == null ? 0 : friends.size()) + '}';
    }
}
