package com.queueforge.job.dto;

import com.queueforge.job.JobType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record JobSubmitRequest(
        @NotNull(message = "Job type is required") JobType type,
        String queue,
        @Min(1) @Max(10) Integer priority,
        @NotNull(message = "Payload is required") Map<String, Object> payload,
        Instant runAt,
        RetryPolicy retryPolicy
) {
    public record RetryPolicy(
            @Min(1) @Max(10) Integer maxAttempts,
            @Min(0) Integer initialDelaySeconds,
            @Min(0) Integer maxDelaySeconds
    ) {}
}