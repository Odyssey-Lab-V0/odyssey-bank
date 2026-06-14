package com.banking.shared.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class OutboxEvent {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    // Stored at write time (while HTTP request is still live) so the relay
    // can re-attach to the same trace when publishing to Kafka
    @Column(name = "trace_id")
    private String traceId;

    public OutboxEvent(UUID eventId, String aggregateType, UUID aggregateId,
                       String eventType, String payload) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public OutboxEvent(UUID eventId, String aggregateType, UUID aggregateId,
                       String eventType, String payload, String traceId) {
        this(eventId, aggregateType, aggregateId, eventType, payload);
        this.traceId = traceId;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }

    public enum OutboxStatus {
        PENDING, PUBLISHED, FAILED
    }
}
