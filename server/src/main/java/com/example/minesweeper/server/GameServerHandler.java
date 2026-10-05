package com.example.minesweeper.server;

import com.example.minesweeper.dao.LeaderboardDao;
import com.example.minesweeper.dao.ScoreDao;
import com.example.minesweeper.dao.UserDao;
import com.example.minesweeper.model.ScoreEntry;
import com.example.minesweeper.model.User;
import com.example.minesweeper.protocol.GameMessage;
import com.example.minesweeper.protocol.MessageType;
import com.example.minesweeper.protocol.payload.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;

public class GameServerHandler extends SimpleChannelInboundHandler<GameMessage> {
    private static final Logger log = LoggerFactory.getLogger(GameServerHandler.class);

    private final UserDao userDao = new UserDao();
    private final ScoreDao scoreDao = new ScoreDao();
    private final LeaderboardDao leaderboardDao = new LeaderboardDao();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Client has connected: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
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

        scoreDao.saveRecord(userId, req.difficulty,
                req.durationSeconds, req.win);

        if (req.win) {
            scoreDao.updateBestIfBetter(userId, req.difficulty,
                    req.durationSeconds);
        }

        var resp = new SubmitScoreResponse(true, "Результат сохранён");
        ctx.writeAndFlush(new GameMessage(MessageType.SUBMIT_SCORE_RESPONSE, resp));
    }

    private void handleLeaderboard(ChannelHandlerContext ctx,
                                   LeaderboardRequest req) throws Exception {
        List<ScoreEntry> top = leaderboardDao.top(req.difficulty, 10);
        List<LeaderboardResponse.Entry> out = new ArrayList<>();
        for (ScoreEntry e : top) {
            out.add(new LeaderboardResponse.Entry(e.username, e.bestTimeSeconds));
        }
        var resp = new LeaderboardResponse(req.difficulty, out);
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
}