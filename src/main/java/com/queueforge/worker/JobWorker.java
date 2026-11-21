package com.queueforge.worker;

import com.queueforge.config.RabbitConfig;
import com.queueforge.handler.HandlerRegistry;
import com.queueforge.handler.JobHandler;
import com.queueforge.job.Job;
import com.queueforge.job.JobRepository;
import com.queueforge.job.JobStateMachine;
import com.queueforge.job.JobStatus;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JobWorker {

    private final JobRepository jobRepository;
    private final JobStateMachine stateMachine;
    private final HandlerRegistry registry;

    public JobWorker(JobRepository jobRepository, JobStateMachine stateMachine, HandlerRegistry registry) {
        this.jobRepository = jobRepository;
        this.stateMachine = stateMachine;
        this.registry = registry;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_NAME)
    public void processJob(String jobIdString) {
        UUID jobId = UUID.fromString(jobIdString);
        System.out.println("👷 WORKER picked up message for Job: " + jobId);

        try {
            // 1. Fetch job and check if it's still eligible
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() != JobStatus.QUEUED) {
                System.out.println("⚠️ Job is no longer QUEUED. Acknowledging and skipping.");
                return; // Stale message
            }

            // 2. Transition to RUNNING
            stateMachine.transition(jobId, JobStatus.RUNNING, "WORKER", "worker-1", null);

            // 3. Execute handler
            JobHandler handler = registry.getHandler(job.getJobType());
            handler.validate(job.getPayload());
            handler.execute(job.getPayload());

            // 4. Transition to SUCCEEDED
            stateMachine.transition(jobId, JobStatus.SUCCEEDED, "WORKER", "worker-1", null);
            System.out.println("✅ WORKER finished Job: " + jobId);

        } catch (Exception e) {
            System.err.println("❌ WORKER failed Job: " + jobId + " - " + e.getMessage());

            // Re-fetch the job to get the latest state before handling failure
            Job failedJob = jobRepository.findById(jobId).orElseThrow();
            stateMachine.handleFailure(failedJob, e.getMessage());
        }
    }
}