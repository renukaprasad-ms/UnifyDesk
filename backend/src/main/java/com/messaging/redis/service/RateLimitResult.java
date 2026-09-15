package com.messaging.redis.service;

import java.time.Duration;

public record RateLimitResult(
    boolean allowed, long current, long limit, long remaining, Duration retryAfter) {}
