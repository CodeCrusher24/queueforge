package com.queueforge.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface JobRepository extends JpaRepository<Job, UUID> {
    Optional<Job> findByIdempotencyKey(String idempotencyKey);
    List<Job> findByStatusAndNextAttemptAtBefore(JobStatus status, Instant time);
}