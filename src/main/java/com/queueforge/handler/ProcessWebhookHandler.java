package com.queueforge.handler;

import com.queueforge.job.JobType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ProcessWebhookHandler implements JobHandler {
    @Override
    public JobType supports() { return JobType.PROCESS_WEBHOOK; }

    @Override
    public void validate(Map<String, Object> payload) {
        if (!payload.containsKey("url")) {
            throw new IllegalArgumentException("Missing url");
        }
    }

    @Override
    public void execute(Map<String, Object> payload) throws Exception {
        System.out.println("🔗 PROCESSING WEBHOOK: " + payload.get("url"));
        // Simulate a 50% failure rate for demonstration
        if (Math.random() > 0.5) {
            throw new RuntimeException("Simulated webhook timeout");
        }
        System.out.println("🔗 WEBHOOK SUCCESS");
    }
}