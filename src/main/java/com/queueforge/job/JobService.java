package com.queueforge.job;

import com.queueforge.job.dto.JobResponse;
import com.queueforge.job.dto.JobSubmitRequest;
import com.queueforge.outbox.OutboxEvent;
import com.queueforge.outbox.OutboxEventRepository;
import com.queueforge.security.UserPrincipal;
import com.queueforge.user.User;
import com.queueforge.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.queueforge.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobEventRepository jobEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;

    public JobService(JobRepository jobRepository, JobEventRepository jobEventRepository,
                      OutboxEventRepository outboxEventRepository, UserRepository userRepository,
                      RabbitTemplate rabbitTemplate) {
        this.jobRepository = jobRepository;
        this.jobEventRepository = jobEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
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
                return existingJob;
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

        JobEvent event = new JobEvent();
        event.setJobId(job.getId());
        event.setEventType("JobSubmitted");
        event.setToStatus(job.getStatus().name());
        event.setActorType("USER");
        event.setActorId(principal.getUsername());
        jobEventRepository.save(event);

        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateType("JOB");
        outbox.setAggregateId(job.getId());
        outbox.setEventType("JobSubmitted");

        // ---> THE FIX IS HERE <---
        outbox.setPayload(Map.of(
                "jobId", job.getId(),
                "jobType", job.getJobType(),
                "fromStatus", "NONE",
                "toStatus", job.getStatus()
        ));
        outboxEventRepository.save(outbox);

        // Send to RabbitMQ ONLY AFTER the database transaction commits
        if (job.getStatus() == JobStatus.QUEUED) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.ROUTING_KEY, job.getId().toString());
                        }
                    }
            );
        }
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

    public List<JobResponse> getAllJobs() {
        return jobRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(job -> new JobResponse(
                        job.getId(),
                        job.getJobType(),
                        job.getStatus(),
                        job.getPriority(),
                        job.getAttemptCount(),
                        job.getCreatedAt(),
                        job.getRunAt(),
                        false
                ))
                .collect(java.util.stream.Collectors.toList());
    }
}