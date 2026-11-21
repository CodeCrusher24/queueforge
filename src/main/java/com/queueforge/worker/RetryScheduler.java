package com.queueforge.worker;

import com.queueforge.config.LogStreamService;
import com.queueforge.config.RabbitConfig;
import com.queueforge.job.Job;
import com.queueforge.job.JobRepository;
import com.queueforge.job.JobStateMachine;
import com.queueforge.job.JobStatus;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

@Component
public class RetryScheduler {

    private final JobRepository jobRepository;
    private final JobStateMachine stateMachine;
    private final RabbitTemplate rabbitTemplate;
    private final LogStreamService logStreamService;

    public RetryScheduler(JobRepository jobRepository, JobStateMachine stateMachine, RabbitTemplate rabbitTemplate, LogStreamService logStreamService) {
        this.jobRepository = jobRepository;
        this.stateMachine = stateMachine;
        this.rabbitTemplate = rabbitTemplate;
        this.logStreamService = logStreamService;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processRetries() {
        List<Job> jobsToRetry = jobRepository.findByStatusAndNextAttemptAtBefore(JobStatus.RETRY_WAIT, Instant.now());

        for (Job job : jobsToRetry) {
            String shortId = job.getId().toString().substring(0, 8);
            logStreamService.addLog("🔄 Scheduler re-queueing Job: " + shortId);

            stateMachine.transition(job.getId(), JobStatus.QUEUED, "SYSTEM", "scheduler", null);

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.ROUTING_KEY, job.getId().toString());
                        }
                    }
            );
        }
    }
}