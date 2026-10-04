package com.example.minesweeper.protocol;

import com.example.minesweeper.protocol.payload.*;

public enum MessageType {
    REGISTER_REQUEST(RegisterRequest.class),
    REGISTER_RESPONSE(RegisterResponse.class),

    LOGIN_REQUEST(LoginRequest.class),
    LOGIN_RESPONSE(LoginResponse.class),

    SUBMIT_SCORE_REQUEST(SubmitScoreRequest.class),
    SUBMIT_SCORE_RESPONSE(SubmitScoreResponse.class),

    LEADERBOARD_REQUEST(LeaderboardRequest.class),
    LEADERBOARD_RESPONSE(LeaderboardResponse.class),

    PING(null),
    PONG(null),
    ERROR(LoginResponse.class);   // сейчас ERROR шлёт LoginResponse, оставим так

    private final Class<?> payloadClass;

    MessageType(Class<?> payloadClass) {
        this.payloadClass = payloadClass;
    }

    public Class<?> getPayloadClass() {
        return payloadClass;
    }
}