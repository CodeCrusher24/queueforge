package com.queueforge.outbox;

import com.queueforge.config.KafkaConfig;
import com.queueforge.config.LogStreamService;
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
    private final LogStreamService logStreamService;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           LogStreamService logStreamService) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.logStreamService = logStreamService;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(KafkaConfig.TOPIC_NAME, event.getAggregateId().toString(), event.getPayload()).get();

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);

                logStreamService.addLog("📤 Outbox published event: " + event.getEventType() + " for Job: " + event.getAggregateId().toString().substring(0, 8));
            } catch (Exception e) {
                logStreamService.addLog("❌ Failed to publish outbox event: " + event.getId());
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(e.getMessage());
                outboxEventRepository.save(event);
            }
        }
    }
}