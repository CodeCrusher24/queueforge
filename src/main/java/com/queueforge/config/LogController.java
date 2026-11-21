package com.queueforge.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
public class LogController {

    private final LogStreamService logStreamService;
    private final StringRedisTemplate redisTemplate;

    public LogController(LogStreamService logStreamService, StringRedisTemplate redisTemplate) {
        this.logStreamService = logStreamService;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/workers")
    public ResponseEntity<Integer> getActiveWorkers() {
        var keys = redisTemplate.keys("worker:*:heartbeat");
        return ResponseEntity.ok(keys != null ? keys.size() : 0);
    }

    @GetMapping("/logs")
    public ResponseEntity<List<String>> getLogs() {
        return ResponseEntity.ok(logStreamService.getLogs());
    }
}