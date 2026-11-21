package com.queueforge.worker;

import com.queueforge.config.LogStreamService;
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
    private final LogStreamService logStreamService;

    public JobWorker(JobRepository jobRepository, JobStateMachine stateMachine, HandlerRegistry registry, LogStreamService logStreamService) {
        this.jobRepository = jobRepository;
        this.stateMachine = stateMachine;
        this.registry = registry;
        this.logStreamService = logStreamService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_NAME)
    public void processJob(String jobIdString) {
        UUID jobId = UUID.fromString(jobIdString);
        String shortId = jobId.toString().substring(0, 8);
        logStreamService.addLog("👷 WORKER picked up message for Job: " + shortId);

        try {
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() != JobStatus.QUEUED) {
                logStreamService.addLog("⚠️ Job " + shortId + " is no longer QUEUED. Skipping.");
                return;
            }

            stateMachine.transition(jobId, JobStatus.RUNNING, "WORKER", "worker-1", null);
            logStreamService.addLog("▶️ Job " + shortId + " is now RUNNING.");

            JobHandler handler = registry.getHandler(job.getJobType());
            handler.validate(job.getPayload());
            handler.execute(job.getPayload());

            stateMachine.transition(jobId, JobStatus.SUCCEEDED, "WORKER", "worker-1", null);
            logStreamService.addLog("✅ WORKER finished Job: " + shortId);

        } catch (Exception e) {
            logStreamService.addLog("❌ WORKER failed Job: " + shortId + " - " + e.getMessage());
            Job failedJob = jobRepository.findById(jobId).orElseThrow();
            stateMachine.handleFailure(failedJob, e.getMessage());
        }
    }
}