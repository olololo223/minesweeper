package com.example.minesweeper.server;

import com.example.minesweeper.protocol.GameMessageCodec;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;

public class GameServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslContext;

    public GameServerInitializer(SslContext sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        var pipeline = ch.pipeline();

        if (sslContext != null) {
            pipeline.addLast(sslContext.newHandler(ch.alloc()));
        }

        pipeline.addLast(new GameMessageCodec.Decoder());
        pipeline.addLast(new GameMessageCodec.Encoder());
        pipeline.addLast(new RateLimitHandler());       // ← добавили
        pipeline.addLast(new GameServerHandler());
    }
}