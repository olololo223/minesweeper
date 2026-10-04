package com.example.minesweeper.server;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.File;

public final class SslContextFactory {

    private SslContextFactory() {}

    public static SslContext forServer(String certPath, String keyPath) {
        try {
            return SslContextBuilder
                    .forServer(new File(certPath), new File(keyPath))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось создать серверный SSL-контекст", e);
        }
    }
}