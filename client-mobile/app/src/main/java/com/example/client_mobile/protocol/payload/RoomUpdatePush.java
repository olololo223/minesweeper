package com.example.client_mobile.protocol.payload;

import java.util.List;

public class RoomUpdatePush {

    /** Состояние одной клетки, как её видит клиент. */
    public static class CellState {
        public int row;
        public int col;
        public boolean revealed;
        public boolean flagged;
        public int neighborMines;    // -1, если клетка закрыта
        public boolean mine;         // true, если мина и уже открыта (взорвалась)
        public boolean exploded;     // true, если эта мина взорвала кого-то

        public CellState() {}

        public CellState(int row, int col, boolean revealed, boolean flagged,
                         int neighborMines, boolean mine, boolean exploded) {
            this.row = row;
            this.col = col;
            this.revealed = revealed;
            this.flagged = flagged;
            this.neighborMines = neighborMines;
            this.mine = mine;
            this.exploded = exploded;
        }
    }

    /** Игрок комнаты и его статус. */
    public static class PlayerState {
        public long userId;
        public String username;
        public boolean alive;
        public boolean owner;
        public int flagsPlaced;

        public PlayerState() {}

        public PlayerState(long userId, String username, boolean alive,
                           boolean owner, int flagsPlaced) {
            this.userId = userId;
            this.username = username;
            this.alive = alive;
            this.owner = owner;
            this.flagsPlaced = flagsPlaced;
        }
    }

    public long roomId;
    public String state;                       // WAITING / PLAYING / FINISHED
    public int rows;
    public int cols;
    public int totalMines;

    /** Только те клетки, которые изменились с прошлого push'а. */
    public List<CellState> changedCells;

    /** Игроки комнаты и их статус. */
    public List<PlayerState> players;

    public RoomUpdatePush() {}

    @Override
    public String toString() {
        return "RoomUpdatePush{room=" + roomId + ", state=" + state
                + ", cells=" + (changedCells == null ? 0 : changedCells.size())
                + ", players=" + (players == null ? 0 : players.size()) + '}';
    }
}
