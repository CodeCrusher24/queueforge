package com.queueforge.job.dto;

import com.queueforge.job.JobStatus;
import com.queueforge.job.JobType;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        JobType type,
        JobStatus status,
        int priority,
        Instant createdAt,
        Instant runAt,
        boolean existingJob
) {}