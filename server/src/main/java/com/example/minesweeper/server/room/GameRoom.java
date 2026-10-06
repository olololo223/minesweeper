package com.example.minesweeper.server.room;

import io.netty.channel.Channel;
import io.netty.util.concurrent.EventExecutor;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Одна комната: поле, игроки, состояние.
 *
 * Внутреннее состояние меняется только в потоке её executor'а (см. RoomManager).
 * Исключение — {@link #hasPlayer(long)}: его читает RoomManager.findByUser
 * из чужого потока, поэтому карта игроков синхронизированная.
 */
public class GameRoom {

    public enum State { WAITING, PLAYING, FINISHED }

    public enum Difficulty {
        EASY(9, 9, 10),
        MEDIUM(16, 16, 40),
        HARD(16, 30, 99);

        public final int rows, cols, mines;
        Difficulty(int rows, int cols, int mines) {
            this.rows = rows;
            this.cols = cols;
            this.mines = mines;
        }

        public static Difficulty fromString(String s) {
            if (s == null) return EASY;
            return switch (s) {
                case "MEDIUM" -> MEDIUM;
                case "HARD" -> HARD;
                default -> EASY;
            };
        }
    }

    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    public static final int MAX_PLAYERS = 4;

    public final long id;
    public final String name;
    public final Difficulty difficulty;
    public final int rows, cols, totalMines;

    public final Channel ownerChannel;
    public final long ownerUserId;
    public final String ownerUsername;

    /** Потокобезопасный доступ к состоянию. */
    public final EventExecutor executor;

    // ===== Игровое состояние =====

    /**
     * Состояние комнаты. volatile, потому что его читает ещё и
     * handleListRooms из потока Netty.
     */
    public volatile State state = State.WAITING;

    /** Клетки. */
    public final Cell[][] board;

    /**
     * Игроки: userId → Player. Синхронизированная обёртка над LinkedHashMap:
     * порядок входа сохраняется, а одиночные get/put/remove безопасны
     * из чужого потока (нужно для findByUser).
     */
    public final Map<Long, Player> players =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** Кто взорвался (для отображения). */
    public final Set<Long> explodedUsers = new HashSet<>();

    /**
     * Разложены ли мины. В мультиплеере они кладутся по первому клику:
     * тогда первый ход гарантированно безопасен для того, кто кликнул.
     * Меняется только внутри {@link #placeMines(int, int)}.
     */
    private boolean minesPlaced = false;

    public boolean isMinesPlaced() {
        return minesPlaced;
    }

    /** Источник случайности — отдельное поле, чтобы тесты могли задать seed. */
    private final Random random;

    public GameRoom(String name, Difficulty difficulty,
                    Channel ownerChannel, long ownerUserId, String ownerUsername,
                    EventExecutor executor) {
        this(name, difficulty, ownerChannel, ownerUserId, ownerUsername,
                executor, new Random());
    }

    /** Для тестов: детерминированное поле по seed. */
    public GameRoom(String name, Difficulty difficulty,
                    Channel ownerChannel, long ownerUserId, String ownerUsername,
                    EventExecutor executor, long seed) {
        this(name, difficulty, ownerChannel, ownerUserId, ownerUsername,
                executor, new Random(seed));
    }

    /** Общий конструктор — вся инициализация в одном месте. */
    private GameRoom(String name, Difficulty difficulty,
                     Channel ownerChannel, long ownerUserId, String ownerUsername,
                     EventExecutor executor, Random random) {
        this.id = NEXT_ID.getAndIncrement();
        this.name = name;
        this.difficulty = difficulty;
        this.rows = difficulty.rows;
        this.cols = difficulty.cols;
        this.totalMines = difficulty.mines;

        this.ownerChannel = ownerChannel;
        this.ownerUserId = ownerUserId;
        this.ownerUsername = ownerUsername;
        this.executor = executor;
        this.random = random;

        this.board = new Cell[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                board[r][c] = new Cell();

        addPlayer(ownerUserId, ownerUsername, ownerChannel, true);
    }

    // ===== Игроки =====

    public void addPlayer(long userId, String username, Channel channel, boolean isOwner) {
        players.put(userId, new Player(userId, username, channel, isOwner));
    }

    public void removePlayer(long userId) {
        players.remove(userId);
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public boolean hasPlayer(long userId) {
        return players.containsKey(userId);
    }

    public Player player(long userId) {
        return players.get(userId);
    }

    // ===== Логика игры =====

    /**
     * Открыть клетку от имени игрока.
     * @return изменённые клетки; пустой список — ход не засчитан
     */
    public List<int[]> dig(long userId, int row, int col) {
        if (state != State.PLAYING) return List.of();
        Player p = players.get(userId);
        if (p == null || !p.alive) return List.of();
        if (row < 0 || row >= rows || col < 0 || col >= cols) return List.of();

        Cell cell = board[row][col];
        if (cell.revealed || cell.flagged) return List.of();

        placeMines(row, col);

        List<int[]> changed = new ArrayList<>();
        if (cell.mine) {
            cell.revealed = true;
            cell.exploded = true;
            p.alive = false;
            p.exploded = true;                     // ← новое
            explodedUsers.add(userId);
            changed.add(new int[]{row, col});
        } else {
            int before = changed.size();
            floodFill(row, col, changed);
            // Считаем, сколько клеток открыл именно этот игрок
            p.revealedCells += (changed.size() - before);   // ← новое
        }
        return changed;
    }

    /**
     * Открывает клетку и, если рядом нет мин, соседей — итеративно, через стек.
     * Рекурсия здесь опасна на больших полях: глубина равна размеру области.
     */
    private void floodFill(int startR, int startC, List<int[]> changed) {
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startR, startC});

        while (!stack.isEmpty()) {
            int[] rc = stack.pop();
            int r = rc[0], c = rc[1];

            if (r < 0 || r >= rows || c < 0 || c >= cols) continue;
            Cell cell = board[r][c];
            if (cell.revealed || cell.flagged) continue;

            cell.revealed = true;
            changed.add(new int[]{r, c});

            if (cell.neighborMines == 0) {
                // Дубликаты в стеке возможны — отсеются проверкой revealed выше
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) continue;
                        stack.push(new int[]{r + dr, c + dc});
                    }
            }
        }
    }

    /**
     * Поставить или снять флаг.
     * Снять может любой, но счётчик уменьшается тому, кто флаг поставил.
     * @return изменённые клетки; пустой список — ход не засчитан
     */
    public List<int[]> toggleFlag(long userId, int row, int col) {
        if (state != State.PLAYING) return List.of();
        Player p = players.get(userId);
        if (p == null || !p.alive) return List.of();
        if (row < 0 || row >= rows || col < 0 || col >= cols) return List.of();

        Cell cell = board[row][col];
        if (cell.revealed) return List.of();

        if (cell.flagged) {
            Long placerId = cell.flaggedBy;
            cell.flagged = false;
            cell.flaggedBy = null;
            if (placerId != null) {
                Player placer = players.get(placerId);
                if (placer != null) {
                    placer.flagsPlaced = Math.max(0, placer.flagsPlaced - 1);
                }
            }
        } else {
            cell.flagged = true;
            cell.flaggedBy = userId;
            p.flagsPlaced++;
        }
        return List.of(new int[]{row, col});
    }

    /**
     * Разложить мины один раз. Повторный вызов — no-op, поэтому вызывать
     * можно откуда угодно и сколько угодно раз: класс сам следит за флагом,
     * а не полагается на внешний код.
     *
     * @return true, если мины разложены этим вызовом
     */
    public synchronized boolean placeMines(int safeRow, int safeCol) {
        if (minesPlaced) return false;
        minesPlaced = true;

        int placed = 0;
        while (placed < totalMines) {
            int r = random.nextInt(rows);
            int c = random.nextInt(cols);
            if (Math.abs(r - safeRow) <= 1 && Math.abs(c - safeCol) <= 1) continue;
            if (board[r][c].mine) continue;
            board[r][c].mine = true;
            placed++;
        }
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                board[r][c].neighborMines = countNeighborMines(r, c);
        return true;
    }

    private int countNeighborMines(int r, int c) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && board[nr][nc].mine)
                    count++;
            }
        return count;
    }

    /** Клетка. */
    public static class Cell {
        public boolean mine;
        public boolean revealed;
        public boolean flagged;
        public Long flaggedBy;
        public int neighborMines;
        public boolean exploded;    // мина взорвалась
    }

    /** Игрок. */
    public static class Player {
        public final long userId;
        public final String username;
        public final Channel channel;
        public final boolean owner;
        public boolean alive = true;
        public int flagsPlaced = 0;

        // ===== Для статистики мультиплеера =====
        public int revealedCells = 0;   // сколько клеток открыл лично он
        public boolean exploded = false; // взорвался в этой партии

        public Player(long userId, String username, Channel channel, boolean owner) {
            this.userId = userId;
            this.username = username;
            this.channel = channel;
            this.owner = owner;
        }
    }

    /** Все ли не-минные клетки открыты? */
    public boolean allSafeRevealed() {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (!board[r][c].mine && !board[r][c].revealed) return false;
        return true;
    }

    /** Сколько живых игроков. */
    public long alivePlayers() {
        long n = 0;
        for (Player p : players.values()) if (p.alive) n++;
        return n;
    }
}
