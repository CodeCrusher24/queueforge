package com.queueforge.job;

import com.queueforge.outbox.OutboxEvent;
import com.queueforge.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class JobStateMachine {

    private final JobRepository jobRepository;
    private final JobEventRepository jobEventRepository;
    private final OutboxEventRepository outboxEventRepository;

    public JobStateMachine(JobRepository jobRepository,
                           JobEventRepository jobEventRepository,
                           OutboxEventRepository outboxEventRepository) {
        this.jobRepository = jobRepository;
        this.jobEventRepository = jobEventRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public Job transition(UUID jobId, JobStatus targetStatus, String actorType, String actorId, String errorMsg) {
        // 1. Fetch the job
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        JobStatus currentStatus = job.getStatus();

        // 2. Validate the transition (The Rules)
        if (!isValidTransition(currentStatus, targetStatus)) {
            throw new IllegalStateException("Invalid transition from " + currentStatus + " to " + targetStatus);
        }

        // 3. Update the job state
        job.setStatus(targetStatus);
        if (targetStatus == JobStatus.RUNNING) {
            job.setStartedAt(Instant.now());
        } else if (targetStatus == JobStatus.SUCCEEDED || targetStatus == JobStatus.FAILED) {
            job.setFinishedAt(Instant.now());
        }

        if (errorMsg != null) {
            job.setLastErrorMessage(errorMsg);
            job.setLastErrorCode("EXECUTION_ERROR");
        }

        jobRepository.save(job);

        // 4. Record the Audit Event
        JobEvent event = new JobEvent();
        event.setJobId(job.getId());
        event.setEventType(targetStatus.name());
        event.setFromStatus(currentStatus.name());
        event.setToStatus(targetStatus.name());
        event.setActorType(actorType);
        event.setActorId(actorId);
        jobEventRepository.save(event);

        // 5. Record the Outbox Event (for Kafka)
        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateType("JOB");
        outbox.setAggregateId(job.getId());
        outbox.setEventType("JobStatusChanged");
        outbox.setPayload(Map.of(
                "jobId", job.getId(),
                "fromStatus", currentStatus,
                "toStatus", targetStatus
        ));
        outboxEventRepository.save(outbox);

        return job;
    }

    private boolean isValidTransition(JobStatus from, JobStatus to) {
        return switch (from) {
            case SCHEDULED -> to == JobStatus.QUEUED || to == JobStatus.CANCELLED;
            case QUEUED -> to == JobStatus.RUNNING || to == JobStatus.CANCELLED;
            case RUNNING -> to == JobStatus.SUCCEEDED || to == JobStatus.FAILED || to == JobStatus.RETRY_WAIT;
            case RETRY_WAIT -> to == JobStatus.QUEUED || to == JobStatus.FAILED;
            // Terminal states cannot transition to anything else
            case SUCCEEDED, FAILED, CANCELLED, DEAD_LETTERED -> false;
        };
    }
}