package com.example.client_mobile.protocol.payload;

import java.util.List;

public class ListRoomsResponse {
    public static class RoomInfo {
        public long roomId;
        public String roomName;
        public String difficulty;
        public String state;        // WAITING / PLAYING / FINISHED
        public int players;
        public int maxPlayers;      // по умолчанию 4

        public RoomInfo() {}

        public RoomInfo(long roomId, String roomName, String difficulty,
                        String state, int players, int maxPlayers) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.difficulty = difficulty;
            this.state = state;
            this.players = players;
            this.maxPlayers = maxPlayers;
        }
    }

    public List<RoomInfo> rooms;

    public ListRoomsResponse() {}
    public ListRoomsResponse(List<RoomInfo> rooms) {
        this.rooms = rooms;
    }
}
