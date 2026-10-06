package com.example.minesweeper.net;

import com.example.minesweeper.protocol.GameMessage;
import com.example.minesweeper.protocol.GameMessageCodec;
import com.example.minesweeper.protocol.MessageType;
import com.example.minesweeper.protocol.payload.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Обёртка над Netty для клиента.
 *
 * Каждый запрос возвращает CompletableFuture, который завершится
 * при получении ответа нужного типа от сервера (или с ошибкой
 * при таймауте / обрыве соединения).
 *
 * Одновременно можно держать несколько in-flight запросов — они
 * различаются по типу ответа. Если сервер пришлёт два ответа одного
 * типа — второй завершит следующий ожидающий future.
 */
public class GameClient {
    private static final Logger log = LoggerFactory.getLogger(GameClient.class);

    private final String host;
    private final int port;
    private final boolean useTls;
    private final String certPath;

    private EventLoopGroup group;
    private Channel channel;

    // Ожидающие future по типу ответного сообщения.
    private final Map<MessageType, CompletableFuture<?>> pending =
            new ConcurrentHashMap<>();

    /**
     * Слушатели входящих сообщений (для UI). Их может быть несколько:
     * главное окно слушает push'и друзей, окно комнаты — обновления поля.
     * Раньше слушатель был один, и открытие комнаты отключало бейдж друзей.
     */
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    /** Наш userId — узнаём из успешного LoginResponse. */
    private volatile long myUserId = -1;

    public interface MessageListener {
        void onMessage(GameMessage msg);
        void onDisconnected();
    }

    public GameClient(String host, int port,  boolean useTls, String certPath) {
        this.host = host;
        this.port = port;
        this.useTls = useTls;
        this.certPath = certPath;
    }
    public GameClient(String host, int port) {
        this(host, port, false, null);
    }

    /** Заменяет всех слушателей одним — так делает главное окно при старте. */
    public void setListener(MessageListener listener) {
        listeners.clear();
        if (listener != null) addListener(listener);
    }

    /** Добавляет слушателя, не трогая остальных. */
    public void addListener(MessageListener listener) {
        if (listener != null) listeners.add(listener);
    }

    /** Убирает слушателя — окно комнаты делает это при закрытии. */
    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    /** Наш id, или -1 если ещё не логинились. */
    public long getMyUserId() {
        return myUserId;
    }

    // ============ ПОДКЛЮЧЕНИЕ ============

    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> result = new CompletableFuture<>();

        if (channel != null && channel.isActive()) {
            result.complete(null);
            return result;
        }

        group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (useTls) {
                            ch.pipeline().addLast(
                                    ClientSslContextFactory.forClient(certPath)
                                            .newHandler(ch.alloc(), host, port));
                        }
                        ch.pipeline().addLast(new GameMessageCodec.Decoder());
                        ch.pipeline().addLast(new GameMessageCodec.Encoder());
                        ch.pipeline().addLast(new ClientHandler());
                    }
                });

        b.connect(host, port).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                channel = f.channel();
                result.complete(null);
            } else {
                result.completeExceptionally(f.cause());
            }
        });

        return result;
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    // ============ API ЗАПРОСОВ ============

    public CompletableFuture<LoginResponse> login(String username, String password) {
        CompletableFuture<LoginResponse> f = new CompletableFuture<>();
        registerPending(MessageType.LOGIN_RESPONSE, f);
        send(MessageType.LOGIN_REQUEST, new LoginRequest(username, password));
        return f;
    }

    public CompletableFuture<Boolean> submitScore(String difficulty,
                                                  String mode,
                                                  int durationSeconds,
                                                  boolean win) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        CompletableFuture<SubmitScoreResponse> inner = new CompletableFuture<>();
        registerPending(MessageType.SUBMIT_SCORE_RESPONSE, inner);
        inner.whenComplete((resp, err) -> {
            if (err != null) f.completeExceptionally(err);
            else f.complete(resp != null && resp.saved);
        });
        send(MessageType.SUBMIT_SCORE_REQUEST,
                new SubmitScoreRequest(difficulty, mode, durationSeconds, win));
        return f;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<List<LeaderboardResponse.Entry>> getLeaderboard(
            String difficulty, String mode) {
        CompletableFuture<List<LeaderboardResponse.Entry>> f = new CompletableFuture<>();
        CompletableFuture<LeaderboardResponse> inner = new CompletableFuture<>();
        registerPending(MessageType.LEADERBOARD_RESPONSE, inner);
        inner.whenComplete((resp, err) -> {
            if (err != null) f.completeExceptionally(err);
            else f.complete(resp.entries);
        });
        send(MessageType.LEADERBOARD_REQUEST, new LeaderboardRequest(difficulty, mode));
        return f;
    }

    // ============ ЗАКРЫТИЕ ============

    public void close() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    // ============ ВНУТРЕННЕЕ ============

    private void send(MessageType type, Object payload) {
        if (!isConnected()) {
            throw new IllegalStateException("Нет соединения с сервером");
        }
        channel.writeAndFlush(new GameMessage(type, payload));
    }

    private <T> void registerPending(MessageType expectedResponse,
                                     CompletableFuture<T> future) {
        pending.put(expectedResponse, future);

        // Таймаут 5 секунд — если ответ не пришёл, future завершится ошибкой
        future.orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((v, err) -> pending.remove(expectedResponse, future));
    }

    @SuppressWarnings("unchecked")
    private void completePending(MessageType responseType, Object payload) {
        CompletableFuture<Object> f =
                (CompletableFuture<Object>) pending.get(responseType);
        if (f != null) {
            f.complete(payload);
        }
    }

    public CompletableFuture<RegisterResponse> register(String username, String password) {
        CompletableFuture<RegisterResponse> f = new CompletableFuture<>();
        registerPending(MessageType.REGISTER_RESPONSE, f);
        send(MessageType.REGISTER_REQUEST, new RegisterRequest(username, password));
        return f;
    }

    private class ClientHandler extends SimpleChannelInboundHandler<GameMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) {
            // 1) Запоминаем свой id, он нужен UI (например, «я ли владелец комнаты»)
            if (msg.getPayload() instanceof LoginResponse lr && lr.success) {
                myUserId = lr.userId;
            }
            // 2) Завершаем ожидающий future
            completePending(msg.getType(), msg.getPayload());
            // 3) Уведомляем слушателей (для UI)
            for (MessageListener l : listeners) {
                try {
                    l.onMessage(msg);
                } catch (Exception e) {
                    // Исключение в UI-слушателе не должно рвать соединение
                    log.error("Message listener failed on {}", msg.getType(), e);
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // Соединение разорвано — завершаем все ожидающие запросы ошибкой
            CompletableFuture<?>[] all = pending.values().toArray(new CompletableFuture[0]);
            pending.clear();
            for (CompletableFuture<?> f : all) {
                log.warn("Connection with server has been lost");
                f.completeExceptionally(
                        new RuntimeException("Соединение с сервером разорвано"));
            }
            for (MessageListener l : listeners) {
                try {
                    l.onDisconnected();
                } catch (Exception e) {
                    log.error("Listener onDisconnected failed", e);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Network Error", cause);
            ctx.close();
        }
    }

    public CompletableFuture<ChangePasswordResponse> changePassword(String oldPass,
                                                                    String newPass) {
        CompletableFuture<ChangePasswordResponse> f = new CompletableFuture<>();
        registerPending(MessageType.CHANGE_PASSWORD_RESPONSE, f);
        send(MessageType.CHANGE_PASSWORD_REQUEST,
                new ChangePasswordRequest(oldPass, newPass));
        return f;
    }

    public CompletableFuture<MyStatsResponse> getMyStats(int limit) {
        CompletableFuture<MyStatsResponse> f = new CompletableFuture<>();
        registerPending(MessageType.MY_STATS_RESPONSE, f);
        send(MessageType.MY_STATS_REQUEST, new MyStatsRequest(limit));
        return f;
    }

    // ============ ДРУЗЬЯ ============

    public CompletableFuture<FindUserResponse> findUser(String username) {
        CompletableFuture<FindUserResponse> f = new CompletableFuture<>();
        registerPending(MessageType.FIND_USER_RESPONSE, f);
        send(MessageType.FIND_USER_REQUEST, new FindUserRequest(username));
        return f;
    }

    public CompletableFuture<AddFriendResponse> addFriend(long userId) {
        CompletableFuture<AddFriendResponse> f = new CompletableFuture<>();
        registerPending(MessageType.ADD_FRIEND_RESPONSE, f);
        send(MessageType.ADD_FRIEND_REQUEST, new AddFriendRequest(userId));
        return f;
    }

    public CompletableFuture<AcceptFriendResponse> acceptFriend(long requesterUserId) {
        CompletableFuture<AcceptFriendResponse> f = new CompletableFuture<>();
        registerPending(MessageType.ACCEPT_FRIEND_RESPONSE, f);
        send(MessageType.ACCEPT_FRIEND_REQUEST, new AcceptFriendRequest(requesterUserId));
        return f;
    }

    public CompletableFuture<DeclineFriendResponse> declineFriend(long requesterUserId) {
        CompletableFuture<DeclineFriendResponse> f = new CompletableFuture<>();
        registerPending(MessageType.DECLINE_FRIEND_RESPONSE, f);
        send(MessageType.DECLINE_FRIEND_REQUEST, new DeclineFriendRequest(requesterUserId));
        return f;
    }

    public CompletableFuture<RemoveFriendResponse> removeFriend(long friendUserId) {
        CompletableFuture<RemoveFriendResponse> f = new CompletableFuture<>();
        registerPending(MessageType.REMOVE_FRIEND_RESPONSE, f);
        send(MessageType.REMOVE_FRIEND_REQUEST,
                new RemoveFriendRequest(friendUserId, "FRIEND"));
        return f;
    }

    /** Отменить свою исходящую заявку (в отличие от удаления друга). */
    public CompletableFuture<RemoveFriendResponse> cancelFriendRequest(long friendUserId) {
        CompletableFuture<RemoveFriendResponse> f = new CompletableFuture<>();
        registerPending(MessageType.REMOVE_FRIEND_RESPONSE, f);
        send(MessageType.REMOVE_FRIEND_REQUEST,
                new RemoveFriendRequest(friendUserId, "OUTGOING"));
        return f;
    }

    public CompletableFuture<GetFriendsResponse> getFriends() {
        CompletableFuture<GetFriendsResponse> f = new CompletableFuture<>();
        registerPending(MessageType.GET_FRIENDS_RESPONSE, f);
        send(MessageType.GET_FRIENDS_REQUEST, new GetFriendsRequest());
        return f;
    }

    /** Только счётчик входящих заявок — без выгрузки всего списка. */
    public CompletableFuture<CountFriendRequestsResponse> countFriendRequests() {
        CompletableFuture<CountFriendRequestsResponse> f = new CompletableFuture<>();
        registerPending(MessageType.COUNT_FRIEND_REQUESTS_RESPONSE, f);
        send(MessageType.COUNT_FRIEND_REQUESTS_REQUEST, new CountFriendRequestsRequest());
        return f;
    }

    // ============ МУЛЬТИПЛЕЕР: КОМНАТЫ ============

    public CompletableFuture<CreateRoomResponse> createRoom(String difficulty, String roomName) {
        CompletableFuture<CreateRoomResponse> f = new CompletableFuture<>();
        registerPending(MessageType.CREATE_ROOM_RESPONSE, f);
        send(MessageType.CREATE_ROOM_REQUEST, new CreateRoomRequest(difficulty, roomName));
        return f;
    }

    public CompletableFuture<JoinRoomResponse> joinRoom(long roomId) {
        CompletableFuture<JoinRoomResponse> f = new CompletableFuture<>();
        registerPending(MessageType.JOIN_ROOM_RESPONSE, f);
        send(MessageType.JOIN_ROOM_REQUEST, new JoinRoomRequest(roomId));
        return f;
    }

    public CompletableFuture<LeaveRoomResponse> leaveRoom() {
        CompletableFuture<LeaveRoomResponse> f = new CompletableFuture<>();
        registerPending(MessageType.LEAVE_ROOM_RESPONSE, f);
        send(MessageType.LEAVE_ROOM_REQUEST, new LeaveRoomRequest());
        return f;
    }

    public CompletableFuture<ListRoomsResponse> listRooms() {
        CompletableFuture<ListRoomsResponse> f = new CompletableFuture<>();
        registerPending(MessageType.LIST_ROOMS_RESPONSE, f);
        send(MessageType.LIST_ROOMS_REQUEST, new ListRoomsRequest());
        return f;
    }

    public CompletableFuture<StartRoomResponse> startRoom() {
        CompletableFuture<StartRoomResponse> f = new CompletableFuture<>();
        registerPending(MessageType.START_ROOM_RESPONSE, f);
        send(MessageType.START_ROOM_REQUEST, new StartRoomRequest());
        return f;
    }

    /**
     * Ход: ответа у этих запросов нет.
     * Новое состояние поля придёт всем игрокам в ROOM_UPDATE_PUSH.
     */
    public void dig(int row, int col) {
        send(MessageType.DIG_REQUEST, new DigRequest(row, col));
    }

    public void flag(int row, int col) {
        send(MessageType.FLAG_REQUEST, new FlagRequest(row, col));
    }

    /**
     * Тестовый хук: карта мин комнаты.
     * Сервер ответит allowed=false, если запущен без -Ddebug.allowMines=true.
     */
    public CompletableFuture<DebugMinesResponse> debugGetMines(long roomId) {
        CompletableFuture<DebugMinesResponse> f = new CompletableFuture<>();
        registerPending(MessageType.DEBUG_GET_MINES_RESPONSE, f);
        send(MessageType.DEBUG_GET_MINES_REQUEST, new DebugMinesRequest(roomId));
        return f;
    }

    public CompletableFuture<FriendInviteResponse> inviteFriendToRoom(
            long friendUserId, long roomId) {
        CompletableFuture<FriendInviteResponse> f = new CompletableFuture<>();
        registerPending(MessageType.FRIEND_INVITE_RESPONSE, f);
        send(MessageType.FRIEND_INVITE_REQUEST,
                new FriendInviteRequest(friendUserId, roomId));
        return f;
    }

    public CompletableFuture<MpLeaderboardResponse> getMpLeaderboard(int limit) {
        CompletableFuture<MpLeaderboardResponse> f = new CompletableFuture<>();
        registerPending(MessageType.MP_LEADERBOARD_RESPONSE, f);
        send(MessageType.MP_LEADERBOARD_REQUEST, new MpLeaderboardRequest(limit));
        return f;
    }

    public CompletableFuture<MpStatsResponse> getMpStats() {
        CompletableFuture<MpStatsResponse> f = new CompletableFuture<>();
        registerPending(MessageType.MP_STATS_RESPONSE, f);
        send(MessageType.MP_STATS_REQUEST, new MpStatsRequest());
        return f;
    }
}