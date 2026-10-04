package com.example.minesweeper.server;

import io.netty.util.AttributeKey;

public final class SessionKeys {
    public static final AttributeKey<Long> USER_ID =
            AttributeKey.valueOf("userId");
    public static final AttributeKey<String> USERNAME =
            AttributeKey.valueOf("username");

    private SessionKeys() {}
}