package com.example.minesweeper.server;

import com.example.minesweeper.dao.FriendshipDao;
import com.example.minesweeper.dao.LeaderboardDao;
import com.example.minesweeper.dao.ScoreDao;
import com.example.minesweeper.dao.UserDao;
import com.example.minesweeper.dao.MpScoreDao;
import com.example.minesweeper.model.ScoreEntry;
import com.example.minesweeper.model.User;
import com.example.minesweeper.protocol.GameMessage;
import com.example.minesweeper.protocol.MessageType;
import com.example.minesweeper.protocol.payload.*;
import com.example.minesweeper.server.room.GameRoom;
import com.example.minesweeper.server.room.RoomManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

public class GameServerHandler extends SimpleChannelInboundHandler<GameMessage> {
    private static final Logger log = LoggerFactory.getLogger(GameServerHandler.class);

    private final UserDao userDao = new UserDao();
    private final ScoreDao scoreDao = new ScoreDao();
    private final LeaderboardDao leaderboardDao = new LeaderboardDao();
    private final FriendshipDao friendshipDao = new FriendshipDao();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final MpScoreDao mpScoreDao = new MpScoreDao();

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Client has connected: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long userId = ctx.channel().attr(SessionKeys.USER_ID).get();
        if (userId != null) {
            UserChannelRegistry.unregister(userId, ctx.channel());

            // Игрок мог сидеть в комнате — иначе он останется там навсегда
            GameRoom room = RoomManager.findByUser(userId);
            if (room != null) {
                long uid = userId;
                inRoom(room, () -> {
                    log.info("User {} disconnected, removing from room {}", uid, room.id);
                    removeFromRoom(room, uid);
                });
            }
            log.info("User {} disconnected (channel {})", userId, ctx.channel().id());
        }
        log.info("Client has disconnected: {}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GameMessage msg) {
        log.debug("Got: {}", msg.getType());
        try {
            switch (msg.getType()) {
                case PING -> ctx.writeAndFlush(
                        new GameMessage(MessageType.PONG, null));

                case LOGIN_REQUEST -> handleLogin(ctx, (LoginRequest) msg.getPayload());

                case SUBMIT_SCORE_REQUEST -> handleSubmit(ctx,
                        (SubmitScoreRequest) msg.getPayload());

                case LEADERBOARD_REQUEST -> handleLeaderboard(ctx,
                        (LeaderboardRequest) msg.getPayload());
                case REGISTER_REQUEST -> handleRegister(ctx, (RegisterRequest) msg.getPayload());
                case CHANGE_PASSWORD_REQUEST -> handleChangePassword(ctx,
                        (ChangePasswordRequest) msg.getPayload());

                case MY_STATS_REQUEST -> handleMyStats(ctx,
                        (MyStatsRequest) msg.getPayload());

                case FIND_USER_REQUEST -> handleFindUser(ctx,
                        (FindUserRequest) msg.getPayload());
                case ADD_FRIEND_REQUEST -> handleAddFriend(ctx,
                        (AddFriendRequest) msg.getPayload());
                case ACCEPT_FRIEND_REQUEST -> handleAcceptFriend(ctx,
                        (AcceptFriendRequest) msg.getPayload());
                case DECLINE_FRIEND_REQUEST -> handleDeclineFriend(ctx,
                        (DeclineFriendRequest) msg.getPayload());
                case REMOVE_FRIEND_REQUEST -> handleRemoveFriend(ctx,
                        (RemoveFriendRequest) msg.getPayload());
                case GET_FRIENDS_REQUEST -> handleGetFriends(ctx);
                case COUNT_FRIEND_REQUESTS_REQUEST -> handleCountFriendRequests(ctx);

                case CREATE_ROOM_REQUEST -> handleCreateRoom(ctx,
                        (CreateRoomRequest) msg.getPayload());
                case JOIN_ROOM_REQUEST -> handleJoinRoom(ctx,
                        (JoinRoomRequest) msg.getPayload());
                case LEAVE_ROOM_REQUEST -> handleLeaveRoom(ctx);
                case LIST_ROOMS_REQUEST -> handleListRooms(ctx);
                case START_ROOM_REQUEST -> handleStartRoom(ctx);
                case DIG_REQUEST -> handleDig(ctx, (DigRequest) msg.getPayload());
                case FLAG_REQUEST -> handleFlag(ctx, (FlagRequest) msg.getPayload());
                case DEBUG_GET_MINES_REQUEST -> handleDebugMines(ctx,
                        (DebugMinesRequest) msg.getPayload());
                case FRIEND_INVITE_REQUEST -> handleFriendInvite(ctx,
                        (FriendInviteRequest) msg.getPayload());
                case MP_LEADERBOARD_REQUEST -> handleMpLeaderboard(ctx,
                        (MpLeaderboardRequest) msg.getPayload());
                case MP_STATS_REQUEST -> handleMpStats(ctx);
                default -> {
                    var err = new LoginResponse(false,
                            "Неизвестный тип: " + msg.getType(), 0);
                    ctx.writeAndFlush(new GameMessage(MessageType.ERROR, err));
                }
            }
        } catch (Exception e) {
            log.error("Error while processing {}", msg.getType(), e);
            e.printStackTrace();
            var err = new LoginResponse(false,
                    "Ошибка сервера: " + e.getMessage(), 0);
            ctx.writeAndFlush(new GameMessage(MessageType.ERROR, err));
        }
    }
    private void handleRegister(ChannelHandlerContext ctx,
                                RegisterRequest req) throws Exception {
        if (req.username == null || req.username.isBlank()
                || req.username.length() > 50) {
            sendRegisterError(ctx, "Некорректное имя");
            return;
        }
        if (req.password == null || req.password.length() < 4) {
            sendRegisterError(ctx, "Пароль должен быть не короче 4 символов");
            return;
        }

        User existing = userDao.findByUsername(req.username);
        if (existing != null) {
            sendRegisterError(ctx, "Имя уже занято");
            return;
        }

        String hash = passwordEncoder.encode(req.password);
        User u = userDao.create(req.username, hash);

        ctx.writeAndFlush(new GameMessage(MessageType.REGISTER_RESPONSE,
                new RegisterResponse(true, "Регистрация успешна")));
    }
    private void sendRegisterError(ChannelHandlerContext ctx, String msg) {
        ctx.writeAndFlush(new GameMessage(MessageType.REGISTER_RESPONSE,
                new RegisterResponse(false, msg)));
    }
    private void handleLogin(ChannelHandlerContext ctx, LoginRequest req) throws Exception {
        if (req.username == null || req.username.isBlank()
                || req.password == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.LOGIN_RESPONSE,
                    new LoginResponse(false, "Не указано имя или пароль", 0)));
            return;
        }

        User u = userDao.findByUsername(req.username);

        if (u == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.LOGIN_RESPONSE,
                    new LoginResponse(false, "Пользователь не найден", 0)));
            return;
        }

        if (u.passwordHash == null) {
            // Миграция старого пользователя без пароля: сохраняем введённый как новый.
            // В продакшене эту ветку надо убрать и требовать регистрацию заново.
            String hash = passwordEncoder.encode(req.password);
            userDao.setPasswordHash(u.id, hash);
            u.passwordHash = hash;
        } else if (!passwordEncoder.matches(req.password, u.passwordHash)) {
            ctx.writeAndFlush(new GameMessage(MessageType.LOGIN_RESPONSE,
                    new LoginResponse(false, "Неверный пароль", 0)));
            return;
        }

        ctx.channel().attr(SessionKeys.USER_ID).set(u.id);
        ctx.channel().attr(SessionKeys.USERNAME).set(u.username);

        // Регистрируем канал в реестре (вытесняет старое соединение, если было)
        UserChannelRegistry.register(u.id, ctx.channel());
        log.info("User {} logged in (channel {})", u.username, ctx.channel().id());

        ctx.writeAndFlush(new GameMessage(MessageType.LOGIN_RESPONSE,
                new LoginResponse(true, "Добро пожаловать, " + u.username, u.id)));
    }

    private void handleSubmit(ChannelHandlerContext ctx,
                              SubmitScoreRequest req) throws Exception {
        // Берём userId из сессии, а не из запроса
        Long userId = ctx.channel().attr(SessionKeys.USER_ID).get();
        if (userId == null) {
            var err = new SubmitScoreResponse(false,
                    "Сначала нужно войти (LOGIN_REQUEST)");
            ctx.writeAndFlush(new GameMessage(MessageType.ERROR, err));
            return;
        }

        // Валидация mode — только известные значения
        String mode = normalizeMode(req.mode);
        if (mode == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.SUBMIT_SCORE_RESPONSE,
                    new SubmitScoreResponse(false, "Неизвестный режим: " + req.mode)));
            return;
        }

        scoreDao.saveRecord(userId, req.difficulty, mode,
                req.durationSeconds, req.win);

        if (req.win) {
            scoreDao.updateBestIfBetter(userId, req.difficulty, mode,
                    req.durationSeconds);
        }

        var resp = new SubmitScoreResponse(true, "Результат сохранён");
        ctx.writeAndFlush(new GameMessage(MessageType.SUBMIT_SCORE_RESPONSE, resp));
    }

    /** Возвращает "CLASSIC" или "TIMED", либо null при неверном значении. */
    private static String normalizeMode(String mode) {
        if (mode == null) return "CLASSIC";    // для обратной совместимости
        return switch (mode) {
            case "CLASSIC", "TIMED" -> mode;
            default -> null;
        };
    }

    private void handleLeaderboard(ChannelHandlerContext ctx,
                                   LeaderboardRequest req) throws Exception {
        String mode = normalizeMode(req.mode);
        if (mode == null) mode = "CLASSIC";

        List<ScoreEntry> top = leaderboardDao.top(req.difficulty, mode, 10);
        List<LeaderboardResponse.Entry> out = new ArrayList<>();
        for (ScoreEntry e : top) {
            out.add(new LeaderboardResponse.Entry(e.username, e.bestTimeSeconds));
        }
        var resp = new LeaderboardResponse(req.difficulty, mode, out);
        ctx.writeAndFlush(new GameMessage(MessageType.LEADERBOARD_RESPONSE, resp));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error on channel {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    private void handleChangePassword(ChannelHandlerContext ctx,
                                      ChangePasswordRequest req) throws Exception {
        // 1. Кто это? Берём из сессии канала
        Long userId = ctx.channel().attr(SessionKeys.USER_ID).get();
        if (userId == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.CHANGE_PASSWORD_RESPONSE,
                    new ChangePasswordResponse(false, "Сначала войдите в аккаунт")));
            return;
        }

        // 2. Валидация
        if (req.oldPassword == null || req.newPassword == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.CHANGE_PASSWORD_RESPONSE,
                    new ChangePasswordResponse(false, "Не указан пароль")));
            return;
        }
        if (req.newPassword.length() < 4) {
            ctx.writeAndFlush(new GameMessage(MessageType.CHANGE_PASSWORD_RESPONSE,
                    new ChangePasswordResponse(false, "Новый пароль минимум 4 символа")));
            return;
        }
        if (req.newPassword.equals(req.oldPassword)) {
            ctx.writeAndFlush(new GameMessage(MessageType.CHANGE_PASSWORD_RESPONSE,
                    new ChangePasswordResponse(false, "Новый пароль совпадает со старым")));
            return;
        }

        // 3. Найти пользователя и проверить старый пароль
        User u = userDao.findById(userId);
        if (u == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.CHANGE_PASSWORD_RESPONSE,
                    new ChangePasswordResponse(false, "Пользователь не найден")));
            return;
        }
        if (u.passwordHash != null
                && !passwordEncoder.matches(req.oldPassword, u.passwordHash)) {
            log.warn("Change password: wrong old password for user {}", u.username);
            ctx.writeAndFlush(new GameMessage(MessageType.CHANGE_PASSWORD_RESPONSE,
                    new ChangePasswordResponse(false, "Неверный старый пароль")));
            return;
        }

        // 4. Хэшируем и сохраняем
        String newHash = passwordEncoder.encode(req.newPassword);
        userDao.setPasswordHash(userId, newHash);

        log.info("User {} changed password", u.username);
        ctx.writeAndFlush(new GameMessage(MessageType.CHANGE_PASSWORD_RESPONSE,
                new ChangePasswordResponse(true, "Пароль изменён")));
    }

    private void handleMyStats(ChannelHandlerContext ctx,
                               MyStatsRequest req) throws Exception {
        Long userId = ctx.channel().attr(SessionKeys.USER_ID).get();
        if (userId == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.MY_STATS_RESPONSE,
                    errorStats("Сначала войдите в аккаунт")));
            return;
        }

        int limit = (req != null && req.limit > 0 && req.limit <= 200) ? req.limit : 50;

        var byDiff = scoreDao.statsByDifficulty(userId);
        var recent = scoreDao.recentGames(userId, limit);

        int totalGames = 0;
        int totalWins = 0;
        // Итоги по режимам: считаем на сервере, чтобы клиент не пересчитывал
        java.util.Map<String, int[]> byMode = new java.util.LinkedHashMap<>();
        byMode.put("CLASSIC", new int[]{0, 0});
        byMode.put("TIMED", new int[]{0, 0});

        for (var d : byDiff) {
            totalGames += d.totalGames;
            totalWins += d.wins;

            int[] slot = byMode.computeIfAbsent(d.mode, k -> new int[]{0, 0});
            slot[0] += d.totalGames;
            slot[1] += d.wins;
        }

        MyStatsResponse resp = new MyStatsResponse();
        resp.success = true;
        resp.message = "OK";
        resp.totalGames = totalGames;
        resp.totalWins = totalWins;
        resp.byDifficulty = byDiff;
        resp.recentGames = recent;
        resp.modeTotals = new ArrayList<>();
        for (var e : byMode.entrySet()) {
            resp.modeTotals.add(new MyStatsResponse.ModeTotals(
                    e.getKey(), e.getValue()[0], e.getValue()[1]));
        }

        ctx.writeAndFlush(new GameMessage(MessageType.MY_STATS_RESPONSE, resp));
    }

    private MyStatsResponse errorStats(String message) {
        MyStatsResponse r = new MyStatsResponse();
        r.success = false;
        r.message = message;
        r.byDifficulty = java.util.List.of();
        r.recentGames = java.util.List.of();
        r.modeTotals = java.util.List.of();
        return r;
    }

    // ===== ДРУЗЬЯ =====
    // Игрок всегда берётся из сессии канала, а не из запроса:
    // подставить чужой id в payload нельзя.

    private Long requireUserId(ChannelHandlerContext ctx) {
        return ctx.channel().attr(SessionKeys.USER_ID).get();
    }

    private void handleFindUser(ChannelHandlerContext ctx,
                                FindUserRequest req) throws Exception {
        Long userId = requireUserId(ctx);
        FindUserResponse resp = new FindUserResponse();
        if (userId == null) {
            resp.found = false;
            resp.message = "Сначала войдите в аккаунт";
            ctx.writeAndFlush(new GameMessage(MessageType.FIND_USER_RESPONSE, resp));
            return;
        }
        if (req == null || req.username == null || req.username.isBlank()) {
            resp.found = false;
            resp.message = "Введите имя игрока";
            ctx.writeAndFlush(new GameMessage(MessageType.FIND_USER_RESPONSE, resp));
            return;
        }

        var r = friendshipDao.findUser(req.username.trim(), userId);
        if (r == null) {
            resp.found = false;
            resp.message = "Игрок не найден";
        } else {
            resp.found = true;
            resp.userId = r.userId;
            resp.username = r.username;
            resp.relation = r.relation.name();
        }
        ctx.writeAndFlush(new GameMessage(MessageType.FIND_USER_RESPONSE, resp));
    }

    private void handleAddFriend(ChannelHandlerContext ctx,
                                 AddFriendRequest req) throws Exception {
        Long userId = requireUserId(ctx);
        AddFriendResponse resp;
        if (userId == null) {
            resp = new AddFriendResponse(false, "Сначала войдите в аккаунт");
        } else if (req == null || req.targetUserId == userId) {
            resp = new AddFriendResponse(false, "Некорректный игрок");
        } else {
            boolean ok = friendshipDao.createRequest(userId, req.targetUserId);
            resp = ok
                    ? new AddFriendResponse(true, "Заявка отправлена")
                    : new AddFriendResponse(false, "Связь уже существует");

            if (ok) {
                pushFriendRequest(userId, req.targetUserId);
            }
        }
        ctx.writeAndFlush(new GameMessage(MessageType.ADD_FRIEND_RESPONSE, resp));
    }

    /** Отправляет получателю push о новой заявке. Если он оффлайн — ничего не делаем. */
    private void pushFriendRequest(long fromUserId, long toUserId) {
        Channel target = UserChannelRegistry.get(toUserId);
        if (target == null || !target.isActive()) return;

        String fromName = null;
        try {
            User from = userDao.findById(fromUserId);
            if (from != null) fromName = from.username;
        } catch (Exception e) {
            log.warn("Failed to load user {} for push", fromUserId, e);
            return;
        }
        if (fromName == null) return;

        int incoming;
        try {
            incoming = friendshipDao.countIncoming(toUserId);
        } catch (Exception e) {
            log.warn("Failed to count incoming for {}", toUserId, e);
            incoming = 0;
        }

        FriendPush push = new FriendPush("NEW_REQUEST", fromUserId, fromName, incoming);
        target.writeAndFlush(new GameMessage(MessageType.FRIEND_REQUEST_PUSH, push));
        log.info("Pushed friend request from {} to user {}", fromName, toUserId);
    }

    private void handleAcceptFriend(ChannelHandlerContext ctx,
                                    AcceptFriendRequest req) throws Exception {
        Long userId = requireUserId(ctx);
        AcceptFriendResponse resp;
        if (userId == null) {
            resp = new AcceptFriendResponse(false, "Сначала войдите в аккаунт");
        } else if (req == null) {
            resp = new AcceptFriendResponse(false, "Некорректный запрос");
        } else {
            boolean ok = friendshipDao.accept(req.requesterUserId, userId);
            resp = ok
                    ? new AcceptFriendResponse(true, "Заявка принята")
                    : new AcceptFriendResponse(false, "Заявка не найдена");

            if (ok) {
                pushFriendAccepted(userId, req.requesterUserId);
            }
        }
        ctx.writeAndFlush(new GameMessage(MessageType.ACCEPT_FRIEND_RESPONSE, resp));
    }

    /** Сообщает инициатору заявки, что её приняли. */
    private void pushFriendAccepted(long accepterUserId, long requesterUserId) {
        Channel target = UserChannelRegistry.get(requesterUserId);
        if (target == null || !target.isActive()) return;

        String accepterName = null;
        try {
            User u = userDao.findById(accepterUserId);
            if (u != null) accepterName = u.username;
        } catch (Exception e) {
            log.warn("Failed to load user {}", accepterUserId, e);
            return;
        }
        if (accepterName == null) return;

        int incoming;
        try {
            incoming = friendshipDao.countIncoming(requesterUserId);
        } catch (Exception e) {
            incoming = 0;
        }

        FriendPush push = new FriendPush("ACCEPTED", accepterUserId,
                accepterName, incoming);
        target.writeAndFlush(new GameMessage(MessageType.FRIEND_ACCEPTED_PUSH, push));
        log.info("Pushed friend accepted from {} to user {}",
                accepterName, requesterUserId);
    }

    private void handleDeclineFriend(ChannelHandlerContext ctx,
                                     DeclineFriendRequest req) throws Exception {
        Long userId = requireUserId(ctx);
        DeclineFriendResponse resp;
        if (userId == null) {
            resp = new DeclineFriendResponse(false, "Сначала войдите в аккаунт");
        } else if (req == null) {
            resp = new DeclineFriendResponse(false, "Некорректный запрос");
        } else {
            boolean ok = friendshipDao.decline(req.requesterUserId, userId);
            resp = ok
                    ? new DeclineFriendResponse(true, "Заявка отклонена")
                    : new DeclineFriendResponse(false, "Заявка не найдена");
        }
        ctx.writeAndFlush(new GameMessage(MessageType.DECLINE_FRIEND_RESPONSE, resp));
    }

    private void handleRemoveFriend(ChannelHandlerContext ctx,
                                    RemoveFriendRequest req) throws Exception {
        Long userId = requireUserId(ctx);
        RemoveFriendResponse resp;
        if (userId == null) {
            resp = new RemoveFriendResponse(false, "Сначала войдите в аккаунт");
        } else if (req == null) {
            resp = new RemoveFriendResponse(false, "Некорректный запрос");
        } else if ("OUTGOING".equals(req.kind)) {
            // Клиент отменяет свою исходящую заявку
            boolean ok = friendshipDao.cancelOutgoing(userId, req.friendUserId);
            resp = ok
                    ? new RemoveFriendResponse(true, "Заявка отменена")
                    : new RemoveFriendResponse(false, "Заявка не найдена");
        } else {
            // По умолчанию — «удалить из друзей»: только принятая связь
            boolean ok = friendshipDao.remove(userId, req.friendUserId);
            resp = ok
                    ? new RemoveFriendResponse(true, "Удалено")
                    : new RemoveFriendResponse(false, "Друг не найден");
        }
        ctx.writeAndFlush(new GameMessage(MessageType.REMOVE_FRIEND_RESPONSE, resp));
    }

    private void handleCountFriendRequests(ChannelHandlerContext ctx) throws Exception {
        Long userId = requireUserId(ctx);
        CountFriendRequestsResponse resp;
        if (userId == null) {
            resp = new CountFriendRequestsResponse(false,
                    "Сначала войдите в аккаунт", 0);
        } else {
            resp = new CountFriendRequestsResponse(true, "OK",
                    friendshipDao.countIncoming(userId));
        }
        ctx.writeAndFlush(new GameMessage(
                MessageType.COUNT_FRIEND_REQUESTS_RESPONSE, resp));
    }

    // ===== МУЛЬТИПЛЕЕР: КОМНАТЫ =====
    // Состояние комнаты меняется только в executor'е этой комнаты.
    // Проверки, от которых зависит изменение, делаем внутри задачи:
    // иначе двое игроков могут одновременно пройти одну и ту же проверку.

    /** Выполнить действие комнаты в её executor'е; закрытая комната — молча пропускаем. */
    private static void inRoom(GameRoom room, Runnable task) {
        try {
            room.executor.submit(task);
        } catch (RejectedExecutionException e) {
            log.debug("Room {} executor already shut down, task skipped", room.id);
        }
    }

    private void handleCreateRoom(ChannelHandlerContext ctx,
                                  CreateRoomRequest req) throws Exception {
        Long userId = requireUserId(ctx);
        if (userId == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.CREATE_ROOM_RESPONSE,
                    new CreateRoomResponse(false, "Сначала войдите", 0)));
            return;
        }
        if (RoomManager.findByUser(userId) != null) {
            ctx.writeAndFlush(new GameMessage(MessageType.CREATE_ROOM_RESPONSE,
                    new CreateRoomResponse(false, "Вы уже в комнате", 0)));
            return;
        }

        String username = ctx.channel().attr(SessionKeys.USERNAME).get();
        String roomName = (req == null || req.roomName == null || req.roomName.isBlank())
                ? username + "'s room"
                : req.roomName.trim();
        if (roomName.length() > 40) roomName = roomName.substring(0, 40);

        GameRoom.Difficulty diff = GameRoom.Difficulty.fromString(
                req == null ? null : req.difficulty);

        GameRoom room = RoomManager.createRoom(roomName, diff,
                ctx.channel(), userId, username);

        log.info("Room {} created by {} ({})", room.id, username, diff);

        ctx.writeAndFlush(new GameMessage(MessageType.CREATE_ROOM_RESPONSE,
                new CreateRoomResponse(true, "Комната создана", room.id)));

        // Комната только что создана и больше никому не видна,
        // поэтому snapshot можно собрать прямо здесь
        ctx.writeAndFlush(new GameMessage(MessageType.ROOM_UPDATE_PUSH,
                RoomManager.buildSnapshot(room)));
    }

    private void handleJoinRoom(ChannelHandlerContext ctx,
                                JoinRoomRequest req) throws Exception {
        Long userId = requireUserId(ctx);
        if (userId == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.JOIN_ROOM_RESPONSE,
                    new JoinRoomResponse(false, "Сначала войдите", 0)));
            return;
        }
        if (RoomManager.findByUser(userId) != null) {
            ctx.writeAndFlush(new GameMessage(MessageType.JOIN_ROOM_RESPONSE,
                    new JoinRoomResponse(false, "Вы уже в комнате", 0)));
            return;
        }
        GameRoom room = req == null ? null : RoomManager.get(req.roomId);
        if (room == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.JOIN_ROOM_RESPONSE,
                    new JoinRoomResponse(false, "Комната не найдена", 0)));
            return;
        }

        long uid = userId;
        String username = ctx.channel().attr(SessionKeys.USERNAME).get();
        inRoom(room, () -> {
            if (room.state != GameRoom.State.WAITING) {
                ctx.writeAndFlush(new GameMessage(MessageType.JOIN_ROOM_RESPONSE,
                        new JoinRoomResponse(false, "Игра уже началась", 0)));
                return;
            }
            if (room.isFull()) {
                ctx.writeAndFlush(new GameMessage(MessageType.JOIN_ROOM_RESPONSE,
                        new JoinRoomResponse(false, "Комната заполнена", 0)));
                return;
            }
            room.addPlayer(uid, username, ctx.channel(), false);
            log.info("User {} joined room {}", username, room.id);

            ctx.writeAndFlush(new GameMessage(MessageType.JOIN_ROOM_RESPONSE,
                    new JoinRoomResponse(true, "OK", room.id)));

            RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_UPDATE_PUSH,
                    RoomManager.buildSnapshot(room)));
        });
    }

    private void handleLeaveRoom(ChannelHandlerContext ctx) {
        Long userId = requireUserId(ctx);
        GameRoom room = userId == null ? null : RoomManager.findByUser(userId);
        if (room == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.LEAVE_ROOM_RESPONSE,
                    new LeaveRoomResponse(false, "Не в комнате")));
            return;
        }

        long uid = userId;
        inRoom(room, () -> {
            log.info("User {} left room {}", uid, room.id);
            removeFromRoom(room, uid);
        });

        ctx.writeAndFlush(new GameMessage(MessageType.LEAVE_ROOM_RESPONSE,
                new LeaveRoomResponse(true, "OK")));
    }

    /**
     * Убирает игрока из комнаты. Вызывать только из executor'а комнаты.
     * Если уходит владелец — комната закрывается (переизбрание владельца не делаем).
     */
    private void removeFromRoom(GameRoom room, long userId) {
        room.removePlayer(userId);
        if (room.isEmpty()) {
            RoomManager.remove(room.id);
            return;
        }
        if (userId == room.ownerUserId) {
            RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_FINISHED_PUSH,
                    new RoomFinishedPush(room.id, false, "Владелец покинул комнату")));
            RoomManager.remove(room.id);
            return;
        }
        RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_UPDATE_PUSH,
                RoomManager.buildSnapshot(room)));
    }

    private void handleListRooms(ChannelHandlerContext ctx) {
        List<ListRoomsResponse.RoomInfo> out = new ArrayList<>();
        for (GameRoom r : RoomManager.all()) {
            out.add(new ListRoomsResponse.RoomInfo(
                    r.id, r.name, r.difficulty.name(),
                    r.state.name(), r.players.size(), GameRoom.MAX_PLAYERS));
        }
        ctx.writeAndFlush(new GameMessage(MessageType.LIST_ROOMS_RESPONSE,
                new ListRoomsResponse(out)));
    }

    private void handleStartRoom(ChannelHandlerContext ctx) {
        Long userId = requireUserId(ctx);
        GameRoom room = userId == null ? null : RoomManager.findByUser(userId);
        if (room == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.START_ROOM_RESPONSE,
                    new StartRoomResponse(false, "Вы не в комнате")));
            return;
        }

        long uid = userId;
        inRoom(room, () -> {
            if (uid != room.ownerUserId) {
                ctx.writeAndFlush(new GameMessage(MessageType.START_ROOM_RESPONSE,
                        new StartRoomResponse(false, "Только владелец может начать")));
                return;
            }
            if (room.state != GameRoom.State.WAITING) {
                ctx.writeAndFlush(new GameMessage(MessageType.START_ROOM_RESPONSE,
                        new StartRoomResponse(false, "Уже начато")));
                return;
            }

            // Мины НЕ раскладываем: они появятся по первому клику,
            // чтобы никто не подорвался на первом ходе
            room.state = GameRoom.State.PLAYING;
            log.info("Room {} started", room.id);

            ctx.writeAndFlush(new GameMessage(MessageType.START_ROOM_RESPONSE,
                    new StartRoomResponse(true, "Игра началась")));

            RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_UPDATE_PUSH,
                    RoomManager.buildSnapshot(room)));
        });
    }

    private void handleDig(ChannelHandlerContext ctx, DigRequest req) {
        Long userId = requireUserId(ctx);
        GameRoom room = userId == null ? null : RoomManager.findByUser(userId);
        if (room == null || req == null) return;

        long uid = userId;
        inRoom(room, () -> {
            // Границы, статус и «жив ли игрок» проверяет сама комната
            List<int[]> changed = room.dig(uid, req.row, req.col);
            if (changed.isEmpty()) return;

            if (room.allSafeRevealed()) {
                room.state = GameRoom.State.FINISHED;
                RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_UPDATE_PUSH,
                        RoomManager.buildDelta(room, changed)));
                saveMpStats(room, true);
                RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_FINISHED_PUSH,
                        new RoomFinishedPush(room.id, true, "Все клетки открыты!")));
                log.info("Room {} finished: WIN", room.id);
                return;
            }
            if (room.alivePlayers() == 0) {
                room.state = GameRoom.State.FINISHED;
                RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_UPDATE_PUSH,
                        RoomManager.buildDelta(room, changed)));
                saveMpStats(room, true);
                RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_FINISHED_PUSH,
                        new RoomFinishedPush(room.id, false, "Все игроки подорвались")));
                log.info("Room {} finished: LOSS", room.id);
                return;
            }
            RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_UPDATE_PUSH,
                    RoomManager.buildDelta(room, changed)));
        });
    }

    private void handleFlag(ChannelHandlerContext ctx, FlagRequest req) {
        Long userId = requireUserId(ctx);
        GameRoom room = userId == null ? null : RoomManager.findByUser(userId);
        if (room == null || req == null) return;

        long uid = userId;
        inRoom(room, () -> {
            List<int[]> changed = room.toggleFlag(uid, req.row, req.col);
            if (changed.isEmpty()) return;
            RoomManager.broadcast(room, new GameMessage(MessageType.ROOM_UPDATE_PUSH,
                    RoomManager.buildDelta(room, changed)));
        });
    }

    /**
     * Тестовый хук: отдаёт карту мин. Работает только если сервер запущен
     * с -Ddebug.allowMines=true — в проде флаг не ставится.
     */
    private static final boolean DEBUG_ALLOW_MINES =
            Boolean.getBoolean("debug.allowMines");

    private void handleDebugMines(ChannelHandlerContext ctx, DebugMinesRequest req) {
        if (!DEBUG_ALLOW_MINES) {
            ctx.writeAndFlush(new GameMessage(MessageType.DEBUG_GET_MINES_RESPONSE,
                    new DebugMinesResponse(false,
                            "Debug mode disabled", java.util.List.of())));
            return;
        }

        // Уровень 1: только залогиненный игрок
        Long userId = requireUserId(ctx);
        if (userId == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.DEBUG_GET_MINES_RESPONSE,
                    new DebugMinesResponse(false,
                            "Not logged in", java.util.List.of())));
            return;
        }

        // Уровень 1 (продолжение): только своя комната
        GameRoom room = req == null ? null : RoomManager.get(req.roomId);
        if (room == null || !room.hasPlayer(userId)) {
            ctx.writeAndFlush(new GameMessage(MessageType.DEBUG_GET_MINES_RESPONSE,
                    new DebugMinesResponse(false,
                            "Not in this room", java.util.List.of())));
            return;
        }

        // Уровень 2: и только владельцу комнаты — обычный игрок карту не видит
        if (userId != room.ownerUserId) {
            ctx.writeAndFlush(new GameMessage(MessageType.DEBUG_GET_MINES_RESPONSE,
                    new DebugMinesResponse(false,
                            "Only room owner", java.util.List.of())));
            return;
        }

        // Уровень 3: каждый запрос оставляет след в логе
        log.warn("DEBUG_GET_MINES by {} for room {} — debug mode is ON",
                userId, room.id);

        inRoom(room, () -> {
            List<int[]> mines = new ArrayList<>();
            for (int r = 0; r < room.rows; r++)
                for (int c = 0; c < room.cols; c++)
                    if (room.board[r][c].mine) mines.add(new int[]{r, c});
            ctx.writeAndFlush(new GameMessage(MessageType.DEBUG_GET_MINES_RESPONSE,
                    new DebugMinesResponse(true, "OK", mines)));
        });
    }

    private void handleGetFriends(ChannelHandlerContext ctx) throws Exception {
        Long userId = requireUserId(ctx);
        GetFriendsResponse resp = new GetFriendsResponse();
        if (userId == null) {
            resp.success = false;
            resp.message = "Сначала войдите в аккаунт";
            resp.friends = java.util.List.of();
        } else {
            resp.success = true;
            resp.message = "OK";
            resp.friends = friendshipDao.listAll(userId);
        }
        ctx.writeAndFlush(new GameMessage(MessageType.GET_FRIENDS_RESPONSE, resp));
    }

    private void handleFriendInvite(ChannelHandlerContext ctx,
                                    FriendInviteRequest req) throws Exception {
        Long inviterId = requireUserId(ctx);
        FriendInviteResponse resp;

        if (inviterId == null) {
            resp = new FriendInviteResponse(false, "Сначала войдите в аккаунт");
        } else if (req == null || req.friendUserId == inviterId) {
            resp = new FriendInviteResponse(false, "Некорректный получатель");
        } else {
            // 1. Инвайтер должен быть в комнате, и она должна быть в WAITING
            GameRoom room = RoomManager.findByUser(inviterId);
            if (room == null) {
                resp = new FriendInviteResponse(false, "Вы не в комнате");
            } else if (room.id != req.roomId) {
                resp = new FriendInviteResponse(false, "Неверная комната");
            } else if (room.state != GameRoom.State.WAITING) {
                resp = new FriendInviteResponse(false, "Игра уже началась");
            } else if (room.isFull()) {
                resp = new FriendInviteResponse(false, "Комната заполнена");
            } else if (!friendshipDao.areFriends(inviterId, req.friendUserId)) {
                resp = new FriendInviteResponse(false, "Он не ваш друг");
            } else {
                // 2. Отправляем push получателю (если он онлайн)
                Channel target = UserChannelRegistry.get(req.friendUserId);
                if (target == null || !target.isActive()) {
                    resp = new FriendInviteResponse(false, "Друг не в сети");
                } else {
                    String inviterName = ctx.channel().attr(SessionKeys.USERNAME).get();
                    FriendInvitePush push = new FriendInvitePush(
                            inviterId, inviterName, room.id, room.name,
                            room.difficulty.name());
                    target.writeAndFlush(new GameMessage(
                            MessageType.FRIEND_INVITE_PUSH, push));

                    log.info("User {} invited {} to room {}",
                            inviterName, req.friendUserId, room.id);
                    resp = new FriendInviteResponse(true, "Приглашение отправлено");
                }
            }
        }
        ctx.writeAndFlush(new GameMessage(MessageType.FRIEND_INVITE_RESPONSE, resp));
    }
    /**
     * Сохраняет статистику мультиплеера всем игрокам комнаты.
     * Вызывается из executor'а комнаты в момент финиша.
     */
    private void saveMpStats(GameRoom room, boolean won) {
        try {
            for (GameRoom.Player p : room.players.values()) {
                int score = RoomManager.scoreFor(room, p, won);
                mpScoreDao.saveRecord(
                        p.userId,
                        room.id,
                        room.difficulty.name(),
                        p.revealedCells,
                        p.exploded,
                        won,
                        score);
                mpScoreDao.updateLeaderboard(p.userId, score, won);
            }
            log.info("Saved MP stats for room {}: won={}, players={}",
                    room.id, won, room.players.size());
        } catch (Exception e) {
            log.error("Failed to save MP stats for room " + room.id, e);
        }
    }

    private void handleMpLeaderboard(ChannelHandlerContext ctx,
                                     MpLeaderboardRequest req) throws Exception {
        int limit = (req != null && req.limit > 0 && req.limit <= 100) ? req.limit : 20;
        var rows = mpScoreDao.topScores(limit);
        var entries = new ArrayList<MpLeaderboardResponse.Entry>();
        for (var r : rows) {
            entries.add(new MpLeaderboardResponse.Entry(
                    (String) r[0],
                    ((Number) r[1]).longValue(),
                    ((Number) r[2]).intValue(),
                    ((Number) r[3]).intValue()));
        }
        ctx.writeAndFlush(new GameMessage(MessageType.MP_LEADERBOARD_RESPONSE,
                new MpLeaderboardResponse(true, "OK", entries)));
    }

    private void handleMpStats(ChannelHandlerContext ctx) throws Exception {
        Long userId = requireUserId(ctx);
        if (userId == null) {
            ctx.writeAndFlush(new GameMessage(MessageType.MP_STATS_RESPONSE,
                    new MpStatsResponse()));
            return;
        }
        var s = mpScoreDao.statsForUser(userId);
        MpStatsResponse resp = new MpStatsResponse();
        resp.success = true;
        resp.message = "OK";
        resp.totalScore = s.totalScore;
        resp.totalGames = s.totalGames;
        resp.totalWins = s.totalWins;
        resp.totalRevealed = s.totalRevealed;
        resp.avgRevealed = s.avgRevealed;
        resp.bestScore = s.bestScore;
        ctx.writeAndFlush(new GameMessage(MessageType.MP_STATS_RESPONSE, resp));
    }
}