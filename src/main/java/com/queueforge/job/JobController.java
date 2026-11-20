package com.queueforge.job;

import com.queueforge.job.dto.JobResponse;
import com.queueforge.job.dto.JobSubmitRequest;
import com.queueforge.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> submitJob(
            @Valid @RequestBody JobSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {

        Job job = jobService.submitJob(request, idempotencyKey, principal);

        // If the job already existed, we return 200 OK, otherwise 201 Created
        boolean isNew = job.getCreatedAt().equals(job.getUpdatedAt());
        HttpStatus status = isNew ? HttpStatus.CREATED : HttpStatus.OK;

        JobResponse response = new JobResponse(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getPriority(),
                job.getCreatedAt(),
                job.getRunAt(),
                !isNew
        );

        return ResponseEntity.status(status).body(response);
    }
}