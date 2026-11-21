package com.queueforge.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String userId) {
        String key = "rate_limit:jobs:" + userId;

        // Increment the counter for this user
        Long count = redisTemplate.opsForValue().increment(key);

        // If this is the first request, set a 60-second expiration
        if (count != null && count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }

        // Allow up to 10 jobs per minute
        return count != null && count <= 10;
    }
}