package com.ricardi.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ricardi.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Needs a Redis on localhost:6379 (run `docker compose up -d` first).
 *
 * TODO: swap this for a Testcontainers-based test so it runs anywhere:
 * add org.testcontainers:testcontainers + junit-jupiter dependencies and
 * start a GenericContainer("redis:7-alpine") in a @DynamicPropertySource.
 */
@SpringBootTest
class RateLimiterServiceTest {

    @Autowired
    RateLimiterService rateLimiter;

    @Test
    void allowsRequestsUpToCapacityThenBlocks() {
        String key = "test-" + System.nanoTime();
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryAcquire(key), "request " + (i + 1) + " should be allowed");
        }
        assertFalse(rateLimiter.tryAcquire(key), "11th request should be rate limited");
    }
}
