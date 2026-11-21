package com.queueforge.handler;

import com.queueforge.job.JobType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class GenerateReportHandler implements JobHandler {
    @Override
    public JobType supports() { return JobType.GENERATE_REPORT; }

    @Override
    public void validate(Map<String, Object> payload) {
        if (!payload.containsKey("reportName")) {
            throw new IllegalArgumentException("Missing reportName");
        }
    }

    @Override
    public void execute(Map<String, Object> payload) throws Exception {
        System.out.println("📊 GENERATING REPORT: " + payload.get("reportName") + "...");
        Thread.sleep(3000); // Simulate heavy work
        System.out.println("📊 REPORT GENERATED");
    }
}