package com.example.client_mobile.protocol.payload;

import java.util.List;

public class DebugMinesResponse {
    public boolean allowed;             // false — если сервер не в debug-режиме
    public String message;
    public List<int[]> mines;           // пары (row, col)

    public DebugMinesResponse() {}

    public DebugMinesResponse(boolean allowed, String message, List<int[]> mines) {
        this.allowed = allowed;
        this.message = message;
        this.mines = mines;
    }

    @Override
    public String toString() {
        return "DebugMinesResponse{allowed=" + allowed + ", mines="
                + (mines == null ? 0 : mines.size()) + '}';
    }
}
