package com.ricardi.ratelimiter.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Token-bucket rate limiter backed by Redis.
 *
 * <p>Plain English: imagine each API client has a bucket that holds tokens.
 * Every request costs one token. The bucket slowly refills over time. If the
 * bucket is empty, the request is rejected until tokens refill. The bucket
 * lives in Redis so every instance of your app sees the same bucket —
 * that's what makes it work when you run multiple servers.</p>
 *
 * <p>The whole check-and-update runs inside a single Redis Lua script, which
 * Redis executes atomically. Without that, two servers could read "1 token
 * left" at the same time and both let a request through.</p>
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    private final int capacity;
    private final int refillPerSecond;

    public RateLimiterService(
            StringRedisTemplate redis,
            @Value("${ratelimiter.capacity:10}") int capacity,
            @Value("${ratelimiter.refill-per-second:1}") int refillPerSecond) {
        this.redis = redis;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("rate-limit.lua"));
        this.script.setResultType(Long.class);
    }

    /**
     * Try to consume one token for the given key.
     *
     * @param key identifies the client (IP address, API key, user id, ...)
     * @return true if the request is allowed, false if it is rate limited
     */
    public boolean tryAcquire(String key) {
        long nowMillis = System.currentTimeMillis();
        Long result = redis.execute(
                script,
                List.of("ratelimit:" + key),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond),
                String.valueOf(nowMillis),
                "1");
        return result != null && result == 1L;
    }
}
