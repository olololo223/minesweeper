package com.example.minesweeper.net;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.File;

/**
 * Фабрика клиентского SSL-контекста.
 *
 * Клиент доверяет ровно одному сертификату — публичной части сертификата
 * сервера (server-cert.pem). Это самоподписанный сертификат, цепочки CA
 * у нас нет, поэтому используется trustManager(File).
 */
public final class ClientSslContextFactory {

    private ClientSslContextFactory() {}

    /**
     * @param certPath путь к публичному сертификату сервера (PEM)
     * @return настроенный SslContext для клиента
     */
    public static SslContext forClient(String certPath) {
        if (certPath == null || certPath.isBlank()) {
            throw new IllegalArgumentException("Не указан путь к сертификату сервера");
        }
        File cert = new File(certPath);
        if (!cert.exists()) {
            throw new IllegalArgumentException(
                    "Файл сертификата не найден: " + cert.getAbsolutePath());
        }
        try {
            return SslContextBuilder
                    .forClient()
                    .trustManager(cert)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось создать клиентский SSL-контекст", e);
        }
    }
}