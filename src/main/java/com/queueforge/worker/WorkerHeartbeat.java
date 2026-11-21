package com.queueforge.worker;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class WorkerHeartbeat {

    private final StringRedisTemplate redisTemplate;
    private final String workerId;

    public WorkerHeartbeat(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String getWorkerId() {
        return workerId;
    }

    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void sendHeartbeat() {
        String key = "worker:" + workerId + ":heartbeat";
        // Write to Redis with a 30-second Time-To-Live
        redisTemplate.opsForValue().set(key, "alive", 30, TimeUnit.SECONDS);
        System.out.println("💓 Heartbeat sent for " + workerId);
    }
}