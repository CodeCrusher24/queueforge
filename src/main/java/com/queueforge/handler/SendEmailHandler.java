package com.queueforge.handler;

import com.queueforge.job.JobType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class SendEmailHandler implements JobHandler {
    @Override
    public JobType supports() { return JobType.SEND_EMAIL; }

    @Override
    public void validate(Map<String, Object> payload) {
        if (!payload.containsKey("recipient") || !payload.containsKey("subject")) {
            throw new IllegalArgumentException("Missing recipient or subject");
        }
    }

    @Override
    public void execute(Map<String, Object> payload) throws Exception {
        // Simulate network delay
        Thread.sleep(1500);
        System.out.println("📧 SIMULATED EMAIL SENT TO: " + payload.get("recipient"));
    }
}