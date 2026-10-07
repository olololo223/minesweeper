package com.example.client_mobile.protocol.payload;

public class RemoveFriendRequest {
    public long friendUserId;

    /**
     * Что именно убираем:
     * FRIEND   — принятую дружбу (кнопка «Удалить из друзей»),
     * OUTGOING — свою исходящую заявку (кнопка «Отменить заявку»).
     * Пусто/null трактуется как FRIEND.
     */
    public String kind;

    public RemoveFriendRequest() {}
    public RemoveFriendRequest(long friendUserId) {
        this(friendUserId, "FRIEND");
    }
    public RemoveFriendRequest(long friendUserId, String kind) {
        this.friendUserId = friendUserId;
        this.kind = kind;
    }
}
