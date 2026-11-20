package com.queueforge.job;

import com.queueforge.job.dto.JobSubmitRequest;
import com.queueforge.outbox.OutboxEvent;
import com.queueforge.outbox.OutboxEventRepository;
import com.queueforge.security.UserPrincipal;
import com.queueforge.user.User;
import com.queueforge.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobEventRepository jobEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UserRepository userRepository;

    public JobService(JobRepository jobRepository, JobEventRepository jobEventRepository,
                      OutboxEventRepository outboxEventRepository, UserRepository userRepository) {
        this.jobRepository = jobRepository;
        this.jobEventRepository = jobEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Job submitJob(JobSubmitRequest request, String idempotencyKey, UserPrincipal principal) {
        String requestHash = hashRequest(request);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Job> existing = jobRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Job existingJob = existing.get();
                if (!existingJob.getRequestHash().equals(requestHash)) {
                    throw new IllegalArgumentException("Idempotency key reused with different payload");
                }
                return existingJob; // Return existing job safely
            }
        }

        User user = userRepository.getReferenceById(principal.getId());

        Job job = new Job();
        job.setJobType(request.type());
        job.setQueueName(request.queue() != null ? request.queue() : "default");
        job.setPayload(request.payload());
        job.setPriority(request.priority() != null ? request.priority() : 5);
        job.setIdempotencyKey(idempotencyKey);
        job.setRequestHash(requestHash);
        job.setCreatedBy(user);

        Instant runAt = request.runAt() != null ? request.runAt() : Instant.now();
        job.setRunAt(runAt);
        job.setStatus(runAt.isAfter(Instant.now()) ? JobStatus.SCHEDULED : JobStatus.QUEUED);

        if (request.retryPolicy() != null && request.retryPolicy().maxAttempts() != null) {
            job.setMaxAttempts(request.retryPolicy().maxAttempts());
        }

        jobRepository.save(job);

        // 1. Audit Event
        JobEvent event = new JobEvent();
        event.setJobId(job.getId());
        event.setEventType("JobSubmitted");
        event.setToStatus(job.getStatus().name());
        event.setActorType("USER");
        event.setActorId(principal.getUsername());
        jobEventRepository.save(event);

        // 2. Outbox Event
        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateType("JOB");
        outbox.setAggregateId(job.getId());
        outbox.setEventType("JobSubmitted");
        outbox.setPayload(Map.of("jobId", job.getId(), "jobType", job.getJobType(), "status", job.getStatus()));
        outboxEventRepository.save(outbox);

        return job;
    }

    private String hashRequest(JobSubmitRequest request) {
        try {
            String raw = request.type() + ":" + request.queue() + ":" + request.payload().toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request", e);
        }
    }
}