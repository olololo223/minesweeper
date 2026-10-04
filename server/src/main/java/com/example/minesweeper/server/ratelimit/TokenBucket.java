package com.example.minesweeper.server.ratelimit;

/**
 * Простое ведро токенов. Потокобезопасно.
 *
 * capacity     — максимальное число токенов в ведре (burst)
 * refillPerSec — сколько токенов добавляется в секунду
 *
 * tryConsume(1) возвращает true, если токен удалось списать,
 * иначе false — лимит превышен.
 */
public class TokenBucket {

    private final double capacity;
    private final double refillPerSec;

    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(double capacity, double refillPerSec) {
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        this.tokens = capacity;   // стартуем с полным ведром
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryConsume(int n) {
        refill();
        if (tokens >= n) {
            tokens -= n;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSec <= 0) return;
        tokens = Math.min(capacity, tokens + elapsedSec * refillPerSec);
        lastRefillNanos = now;
    }
}