package com.example.minesweeper.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameServer {
    private static final Logger log = LoggerFactory.getLogger(GameServer.class);

    private final int port;
    private final SslContext sslContext;

    public GameServer(int port, SslContext sslContext) {
        this.port = port;
        this.sslContext = sslContext;
    }

    public void run() throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new GameServerInitializer(sslContext));

            ChannelFuture f = b.bind(port).sync();
            System.out.println("[server] Слушаю порт " + port
                    + (sslContext != null ? " (TLS)" : " (plain)"));
            f.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(
                com.example.minesweeper.db.DataSourceProvider::shutdown));

        SslContext ssl = null;
        String cert = System.getProperty("tls.cert");
        String key  = System.getProperty("tls.key");
        if (cert != null && key != null) {
            ssl = SslContextFactory.forServer(cert, key);
            System.out.println("[server] TLS включён: cert=" + cert + ", key=" + key);
        } else {
            System.out.println("[server] TLS не задан — работаем без шифрования");
        }

        new GameServer(9000, ssl).run();
    }
}