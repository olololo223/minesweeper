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

    // Слушатель всех входящих сообщений (для UI). Может быть null.
    private volatile MessageListener listener;

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

    public void setListener(MessageListener listener) {
        this.listener = listener;
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
                new SubmitScoreRequest(difficulty, durationSeconds, win));
        return f;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<List<LeaderboardResponse.Entry>> getLeaderboard(
            String difficulty) {
        CompletableFuture<List<LeaderboardResponse.Entry>> f = new CompletableFuture<>();
        CompletableFuture<LeaderboardResponse> inner = new CompletableFuture<>();
        registerPending(MessageType.LEADERBOARD_RESPONSE, inner);
        inner.whenComplete((resp, err) -> {
            if (err != null) f.completeExceptionally(err);
            else f.complete(resp.entries);
        });
        send(MessageType.LEADERBOARD_REQUEST, new LeaderboardRequest(difficulty));
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
            // 1) Завершаем ожидающий future
            completePending(msg.getType(), msg.getPayload());
            // 2) Уведомляем слушателя (для UI)
            MessageListener l = listener;
            if (l != null) {
                l.onMessage(msg);
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
            MessageListener l = listener;
            if (l != null) l.onDisconnected();
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
}