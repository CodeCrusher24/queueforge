package com.queueforge.analytics;

import com.queueforge.config.KafkaConfig;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AnalyticsConsumer {

    @KafkaListener(topics = KafkaConfig.TOPIC_NAME, groupId = "queueforge-analytics")
    public void consumeJobEvent(Map<String, Object> eventPayload) {
        // We receive the JSON payload from the OutboxEvent
        String jobId = String.valueOf(eventPayload.get("jobId"));
        String fromStatus = String.valueOf(eventPayload.get("fromStatus"));
        String toStatus = String.valueOf(eventPayload.get("toStatus"));

        System.out.println("📊 [KAFKA ANALYTICS] Job " + jobId + " transitioned from " + fromStatus + " to " + toStatus);
    }
}