package com.example.minesweeper.server;

import io.netty.channel.Channel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальный реестр «userId → активный канал».
 *
 * Один пользователь = один канал. Если игрок зашёл с двух устройств,
 * старое соединение вытесняется (храним только последнее).
 *
 * Потокобезопасен, можно вызывать из любых event loop'ов.
 */
public final class UserChannelRegistry {

    private static final ConcurrentHashMap<Long, Channel> MAP =
            new ConcurrentHashMap<>();

    private UserChannelRegistry() {}

    /** Регистрирует канал для пользователя. Возвращает предыдущий канал, если был. */
    public static Channel register(long userId, Channel channel) {
        Channel old = MAP.put(userId, channel);
        if (old != null && old != channel && old.isActive()) {
            // Старый канал закрываем — иначе два клиента будут принимать push
            old.close();
        }
        return old;
    }

    /** Снимает регистрацию, только если текущий канал совпадает. */
    public static void unregister(long userId, Channel channel) {
        MAP.remove(userId, channel);
    }

    /** Канал пользователя или null, если оффлайн. */
    public static Channel get(long userId) {
        return MAP.get(userId);
    }

    /** Множество id всех онлайн-пользователей — пригодится для мультиплеера. */
    public static Set<Long> onlineUsers() {
        return MAP.keySet();
    }

    /** Размер реестра — для мониторинга. */
    public static int size() {
        return MAP.size();
    }
}
