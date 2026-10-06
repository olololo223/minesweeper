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

        // Поиск игроков — 20/мин, burst 30. Защита от перебора имён.
        add(MessageType.FIND_USER_REQUEST, 30, 20.0 / 60);

        // Операции с друзьями — 30/мин, burst 60.
        add(MessageType.ADD_FRIEND_REQUEST, 60, 30.0 / 60);
        add(MessageType.ACCEPT_FRIEND_REQUEST, 60, 30.0 / 60);
        add(MessageType.DECLINE_FRIEND_REQUEST, 60, 30.0 / 60);
        add(MessageType.REMOVE_FRIEND_REQUEST, 60, 30.0 / 60);

        // Запрос списка друзей и счётчика заявок — 60/мин, burst 120.
        add(MessageType.GET_FRIENDS_REQUEST, 120, 60.0 / 60);
        add(MessageType.COUNT_FRIEND_REQUESTS_REQUEST, 120, 60.0 / 60);

        // Комнаты: создание/старт — редко, список — часто, ходы — очень часто.
        add(MessageType.CREATE_ROOM_REQUEST, 5, 3.0 / 60);
        add(MessageType.JOIN_ROOM_REQUEST, 20, 15.0 / 60);
        add(MessageType.LEAVE_ROOM_REQUEST, 20, 15.0 / 60);
        add(MessageType.LIST_ROOMS_REQUEST, 60, 60.0 / 60);
        add(MessageType.START_ROOM_REQUEST, 5, 3.0 / 60);
        add(MessageType.DIG_REQUEST, 300, 120.0 / 60);    // 2 клика/сек, burst 300
        add(MessageType.FLAG_REQUEST, 300, 120.0 / 60);

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