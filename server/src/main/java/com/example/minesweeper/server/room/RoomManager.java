package com.example.minesweeper.server.room;

import com.example.minesweeper.protocol.GameMessage;
import com.example.minesweeper.protocol.payload.RoomUpdatePush;
import io.netty.channel.Channel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр комнат. Каждая комната привязана к своему executor'у —
 * все изменения состояния выполняются в нём, что исключает гонки.
 *
 * Методы buildSnapshot/buildDelta читают состояние комнаты и должны
 * вызываться из executor'а этой комнаты.
 */
public final class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private static final ConcurrentHashMap<Long, GameRoom> ROOMS =
            new ConcurrentHashMap<>();

    /**
     * Общий пул потоков для комнат.
     *
     * Раньше каждая комната создавала свой DefaultEventExecutor (поток на комнату):
     * 500 комнат = 500 потоков и ~500 МБ стеков. Теперь комнаты делят пул,
     * а поток на комнату и не нужен: submit() в один executor сериализует
     * задачи комнаты.
     *
     * Потоки — демоны, чтобы пул не мешал JVM завершиться; плюс есть
     * shutdown() для аккуратной остановки.
     */
    private static final EventExecutorGroup ROOM_EXECUTORS;

    static {
        int cores = Runtime.getRuntime().availableProcessors();
        // По умолчанию 2×ядер (но не меньше 4): запас на паузы ввода-вывода
        int defaultThreads = Math.max(4, 2 * cores);
        int threads = Integer.getInteger("rooms.threads", defaultThreads);
        if (threads < 1) threads = 1;

        ROOM_EXECUTORS = new DefaultEventExecutorGroup(threads, r -> {
            Thread t = new Thread(r, "room-executor");
            t.setDaemon(true);
            return t;
        });

        log.info("Room executor pool initialized: {} threads (cores={})", threads, cores);
    }

    private RoomManager() {}

    /** Сколько потоков в пуле комнат — для мониторинга. */
    public static int poolSize() {
        return ((DefaultEventExecutorGroup) ROOM_EXECUTORS).executorCount();
    }

    public static GameRoom get(long roomId) {
        return ROOMS.get(roomId);
    }

    public static Collection<GameRoom> all() {
        return ROOMS.values();
    }

    public static int count() {
        return ROOMS.size();
    }

    /** Создать комнату. Executor берётся из общего пула. */
    public static GameRoom createRoom(String name,
                                      GameRoom.Difficulty difficulty,
                                      Channel ownerChannel,
                                      long ownerUserId,
                                      String ownerUsername) {
        EventExecutor executor = ROOM_EXECUTORS.next();
        GameRoom room = new GameRoom(name, difficulty, ownerChannel,
                ownerUserId, ownerUsername, executor);
        ROOMS.put(room.id, room);
        log.debug("Room {} created, assigned to {}", room.id, executor);
        return room;
    }

    /**
     * Удалить комнату (когда все вышли).
     * Пул потоков общий, поэтому гасить его здесь нельзя.
     */
    public static void remove(long roomId) {
        ROOMS.remove(roomId);
    }

    /**
     * Остановить пул комнат. Вызывается на завершении приложения,
     * чтобы комнаты не оборвались на середине задачи. Потоки — демоны,
     * так что JVM завершится даже если этот метод не позвать.
     */
    public static void shutdown() {
        ROOM_EXECUTORS.shutdownGracefully();
    }

    /** Найти комнату по userId (в какой он сидит, если в какой-то). */
    public static GameRoom findByUser(long userId) {
        for (GameRoom r : ROOMS.values()) {
            if (r.hasPlayer(userId)) return r;
        }
        return null;
    }

    /**
     * Рассылка push'а всем игрокам комнаты. Можно вызывать из любого потока:
     * Netty сам переключится на event loop каждого канала.
     */
    public static void broadcast(GameRoom room, GameMessage msg) {
        for (GameRoom.Player p : room.players.values()) {
            if (p.channel.isActive()) {
                p.channel.writeAndFlush(msg);
            }
        }
    }

    /** Полный snapshot состояния — для нового игрока или после старта. */
    public static RoomUpdatePush buildSnapshot(GameRoom room) {
        List<int[]> all = new ArrayList<>(room.rows * room.cols);
        for (int r = 0; r < room.rows; r++)
            for (int c = 0; c < room.cols; c++)
                all.add(new int[]{r, c});
        return build(room, all);
    }

    /** Push только с указанными клетками (для оптимизации трафика). */
    public static RoomUpdatePush buildDelta(GameRoom room, List<int[]> changedCoords) {
        return build(room, changedCoords);
    }

    private static RoomUpdatePush build(GameRoom room, List<int[]> coords) {
        RoomUpdatePush push = new RoomUpdatePush();
        push.roomId = room.id;
        push.state = room.state.name();
        push.rows = room.rows;
        push.cols = room.cols;
        push.totalMines = room.totalMines;

        push.changedCells = new ArrayList<>(coords.size());
        for (int[] rc : coords) {
            GameRoom.Cell cell = room.board[rc[0]][rc[1]];
            int neigh = cell.revealed ? cell.neighborMines : -1;
            push.changedCells.add(new RoomUpdatePush.CellState(
                    rc[0], rc[1], cell.revealed, cell.flagged,
                    neigh, cell.mine && cell.revealed, cell.exploded));
        }
        push.players = buildPlayers(room);
        return push;
    }

    private static List<RoomUpdatePush.PlayerState> buildPlayers(GameRoom room) {
        List<RoomUpdatePush.PlayerState> out = new ArrayList<>();
        for (GameRoom.Player p : room.players.values()) {
            out.add(new RoomUpdatePush.PlayerState(
                    p.userId, p.username, p.alive, p.owner, p.flagsPlaced));
        }
        return out;
    }

    /**
     * Считает очки для каждого игрока в комнате.
     * score = revealedCells + (win ? totalSafe / 2 : 0) - (exploded ? 1 : 0)
     */
    public static int scoreFor(GameRoom room, GameRoom.Player p, boolean won) {
        int safe = room.rows * room.cols - room.totalMines;
        int score = p.revealedCells;
        if (won) score += safe / 2;
        if (p.exploded) score -= 1;
        return Math.max(0, score);   // не уходим в минус
    }
}
