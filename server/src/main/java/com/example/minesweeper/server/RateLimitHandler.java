package com.example.minesweeper.server;

import com.example.minesweeper.protocol.GameMessage;
import com.example.minesweeper.protocol.MessageType;
import com.example.minesweeper.protocol.payload.LoginResponse;
import com.example.minesweeper.server.ratelimit.RateLimiter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Лежит в pipeline сразу после декодера.
 * Проверяет лимиты на входящие сообщения и отсекает лишние.
 */
public class RateLimitHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitHandler.class);

    private final RateLimiter limiter = new RateLimiter();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof GameMessage gm) {
            if (!limiter.allow(gm.getType())) {
                log.warn("Limit on {} from {} has been exceeded", gm.getType(), ctx.channel().remoteAddress());
                // Отправляем клиенту ERROR и закрываем канал.
                // Закрывать — правильно: если клиент спамит, нечего держать его.
                ctx.writeAndFlush(new GameMessage(MessageType.ERROR,
                        new LoginResponse(false,
                                "Слишком много запросов. Попробуйте позже.", 0)));
                ctx.close();
                return;
            }
        }
        // Пропускаем дальше
        ctx.fireChannelRead(msg);
    }
}