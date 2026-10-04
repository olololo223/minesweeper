package com.example.minesweeper.protocol;

import java.io.Serializable;

public class GameMessage implements Serializable {
    private final MessageType type;
    private final Object payload;

    public GameMessage(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() { return type; }
    public Object getPayload()   { return payload; }

    @Override
    public String toString() {
        return "GameMessage{" + type + ", payload=" + payload + "}";
    }
}