package com.example.minesweeper.server.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    @Test
    void consumesInitialTokens() {
        TokenBucket b = new TokenBucket(5, 1);
        for (int i = 0; i < 5; i++) {
            assertTrue(b.tryConsume(1), "Token " + i + " should be available");
        }
        assertFalse(b.tryConsume(1), "Bucket should be empty");
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        TokenBucket b = new TokenBucket(2, 10);   // 10 токенов в секунду
        assertTrue(b.tryConsume(2));
        assertFalse(b.tryConsume(1));

        Thread.sleep(250);   // должно накапать ~2.5 токена, но максимум — 2

        assertTrue(b.tryConsume(1), "Should refill");
        assertTrue(b.tryConsume(1), "Should have another");
    }

    @Test
    void capacityIsUpperBound() throws InterruptedException {
        TokenBucket b = new TokenBucket(3, 100);
        Thread.sleep(100);   // 10 токенов должны были накапать, но cap = 3
        for (int i = 0; i < 3; i++) {
            assertTrue(b.tryConsume(1));
        }
        assertFalse(b.tryConsume(1));
    }
}