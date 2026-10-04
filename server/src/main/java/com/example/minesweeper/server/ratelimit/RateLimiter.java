package com.example.minesweeper.server.ratelimit;

import com.example.minesweeper.protocol.MessageType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Набор ведёр для одного канала — по одному на каждый тип сообщения.
 *
 * Логика: сообщение типа X проверяется в ведре X. Это позволяет
 * сделать разные лимиты для логина (жёсткий) и для пинга (мягкий).
 */
public class RateLimiter {

    private final Map<MessageType, TokenBucket> buckets =
            new EnumMap<>(MessageType.class);

    public RateLimiter() {
        // Логин — 5/мин, burst 10. Защита от brute force.
        add(MessageType.LOGIN_REQUEST, 10, 5.0 / 60);

        // Регистрация — 3/мин, burst 5.
        add(MessageType.REGISTER_REQUEST, 5, 3.0 / 60);

        // Отправка результата — 30/мин, burst 60.
        add(MessageType.SUBMIT_SCORE_REQUEST, 60, 30.0 / 60);

        // Рейтинг — 60/мин, burst 120.
        add(MessageType.LEADERBOARD_REQUEST, 120, 60.0 / 60);

        // Всё остальное (PING и т.д.) — 120/мин, burst 240.
        add(MessageType.PING, 240, 120.0 / 60);
    }

    private void add(MessageType type, double capacity, double refillPerSec) {
        buckets.put(type, new TokenBucket(capacity, refillPerSec));
    }

    /**
     * @return true — запрос разрешён, false — превышен лимит
     */
    public boolean allow(MessageType type) {
        TokenBucket b = buckets.get(type);
        if (b == null) {
            // Неизвестный тип — не блокируем (например, ответы сервера)
            return true;
        }
        return b.tryConsume(1);
    }
}