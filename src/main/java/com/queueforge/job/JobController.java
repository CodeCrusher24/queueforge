package com.queueforge.job;

import com.queueforge.config.RedisRateLimiter;
import com.queueforge.job.dto.JobResponse;
import com.queueforge.job.dto.JobSubmitRequest;
import com.queueforge.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final RedisRateLimiter rateLimiter;

    public JobController(JobService jobService, RedisRateLimiter rateLimiter) {
        this.jobService = jobService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> getJobs() {
        return ResponseEntity.ok(jobService.getAllJobs());
    }

    @PostMapping
    public ResponseEntity<?> submitJob(
            @Valid @RequestBody JobSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {

        // Check Redis Rate Limiter
        if (!rateLimiter.isAllowed(principal.getId().toString())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. Please wait a minute.");
        }

        Job job = jobService.submitJob(request, idempotencyKey, principal);

        // If the job already existed, we return 200 OK, otherwise 201 Created
        boolean isNew = job.getCreatedAt().equals(job.getUpdatedAt());
        HttpStatus status = isNew ? HttpStatus.CREATED : HttpStatus.OK;

        // Notice we now have 8 parameters matching the JobResponse record
        JobResponse response = new JobResponse(
                job.getId(),             // 1
                job.getJobType(),        // 2
                job.getStatus(),         // 3
                job.getPriority(),       // 4
                job.getAttemptCount(),   // 5
                job.getCreatedAt(),      // 6
                job.getRunAt(),          // 7 <-- This was missing in your file
                !isNew                   // 8
        );

        return ResponseEntity.status(status).body(response);
    }
}