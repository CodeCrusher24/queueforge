package com.queueforge.outbox;

import com.queueforge.config.KafkaConfig;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000) // Run every 2 seconds
    @Transactional
    public void publishPendingEvents() {
        // 1. Fetch pending events
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                // 2. Send to Kafka. We use the Aggregate ID (Job ID) as the Key so all events for a job go to the same partition.
                kafkaTemplate.send(KafkaConfig.TOPIC_NAME, event.getAggregateId().toString(), event.getPayload()).get(); // .get() waits for Kafka to acknowledge

                // 3. Mark as published
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);

                System.out.println("📤 Outbox published event: " + event.getEventType() + " for Job: " + event.getAggregateId());
            } catch (Exception e) {
                System.err.println("❌ Failed to publish outbox event: " + event.getId() + " - " + e.getMessage());
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(e.getMessage());
                outboxEventRepository.save(event);
            }
        }
    }
}