package com.banking.shared.infrastructure.outbox;

import com.banking.shared.domain.event.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox relay — polls PENDING outbox rows and publishes to Kafka.
 *
 * Each service subclasses this and provides its own OutboxRepository
 * pointing to the correct schema's outbox_events table.
 *
 * Scheduling: runs every 500ms. In production, consider Debezium CDC
 * (Change Data Capture) to react to DB WAL changes instead of polling —
 * lower latency, no polling overhead.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class OutboxEventPublisher<T extends OutboxEvent> {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    protected abstract List<T> findPendingEvents();

    protected abstract void save(T event);

    protected abstract String resolveKafkaTopic(T event);

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPendingEvents() {
        List<T> pending = findPendingEvents();
        if (pending.isEmpty()) return;

        log.debug("Outbox relay: publishing {} pending events", pending.size());

        for (T outboxEvent : pending) {
            try {
                var message = MessageBuilder
                        .withPayload(outboxEvent.getPayload())
                        .setHeader(KafkaHeaders.TOPIC, resolveKafkaTopic(outboxEvent))
                        .setHeader(KafkaHeaders.KEY, outboxEvent.getAggregateId().toString())
                        .setHeader("eventType", outboxEvent.getEventType())
                        .setHeader("eventId", outboxEvent.getEventId().toString())
                        .setHeader("aggregateType", outboxEvent.getAggregateType())
                        .build();

                kafkaTemplate.send(message).get(); // synchronous — within transaction
                outboxEvent.markPublished();
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", outboxEvent.getEventId(), e.getMessage());
                outboxEvent.markFailed();
            }
            save(outboxEvent);
        }
    }

    protected String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize domain event: " + event.eventType(), e);
        }
    }
}
