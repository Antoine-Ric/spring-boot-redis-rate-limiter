package com.ricardi.ratelimiter.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo endpoints to exercise the rate limiter. Hit /api/hello repeatedly
 * and watch it start returning 429 once the bucket is empty.
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "message", "Hello! This endpoint is rate-limited.",
                "at", Instant.now().toString());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("status", "UP");
    }
}
