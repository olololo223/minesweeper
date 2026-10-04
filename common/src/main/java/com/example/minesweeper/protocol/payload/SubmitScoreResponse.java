package com.example.minesweeper.protocol.payload;

public class SubmitScoreResponse {
    public boolean saved;
    public String message;

    public SubmitScoreResponse() {}
    public SubmitScoreResponse(boolean saved, String message) {
        this.saved = saved;
        this.message = message;
    }

    @Override
    public String toString() {
        return "SubmitScoreResponse{saved=" + saved +
                ", message='" + message + '\'' + '}';
    }
}