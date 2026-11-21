package com.queueforge.job;

import com.queueforge.handler.HandlerRegistry;
import com.queueforge.handler.JobHandler;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test")
public class JobTestController {

    private final JobRepository jobRepository;
    private final JobStateMachine stateMachine;
    private final HandlerRegistry registry;

    public JobTestController(JobRepository jobRepository, JobStateMachine stateMachine, HandlerRegistry registry) {
        this.jobRepository = jobRepository;
        this.stateMachine = stateMachine;
        this.registry = registry;
    }

    @PostMapping("/run/{jobId}")
    public String runJobSynchronously(@PathVariable UUID jobId) {
        // 1. Transition to RUNNING
        Job job = stateMachine.transition(jobId, JobStatus.RUNNING, "SYSTEM", "test-endpoint", null);

        try {
            // 2. Get the handler and execute it
            JobHandler handler = registry.getHandler(job.getJobType());
            handler.validate(job.getPayload());
            handler.execute(job.getPayload());

            // 3. Transition to SUCCEEDED
            stateMachine.transition(jobId, JobStatus.SUCCEEDED, "SYSTEM", "test-endpoint", null);
            return "Job executed successfully!";
        } catch (Exception e) {
            // Transition to FAILED if it crashes
            stateMachine.transition(jobId, JobStatus.FAILED, "SYSTEM", "test-endpoint", e.getMessage());
            return "Job failed: " + e.getMessage();
        }
    }
}