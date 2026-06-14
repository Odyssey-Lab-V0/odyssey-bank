package com.banking.iam.infrastructure.messaging;

import com.banking.iam.infrastructure.persistence.entity.IamOutboxEvent;
import com.banking.iam.infrastructure.persistence.repository.OutboxJpaRepository;
import com.banking.shared.infrastructure.outbox.OutboxEvent.OutboxStatus;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IamOutboxRelayService {

    private static final Map<String, String> TOPIC_MAP = Map.of(
            "UserRegistered",    "banking.iam.user.registered.v1",
            "UserStatusChanged", "banking.iam.user.status-changed.v1"
    );

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Tracer tracer;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relay() {
        List<IamOutboxEvent> pending = outboxRepository
                .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pending.isEmpty()) return;

        log.debug("Relaying {} outbox events to Kafka", pending.size());

        for (IamOutboxEvent event : pending) {
            String topic = TOPIC_MAP.getOrDefault(event.getEventType(),
                    "banking.iam." + event.getEventType().toLowerCase() + ".v1");
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, null, event.getAggregateId().toString(), event.getPayload());

                // Re-attach to the original HTTP request trace stored at write time
                // so downstream consumers appear as children of the original request
                String traceId = event.getTraceId();
                if (traceId != null) {
                    String relaySpanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                    record.headers().add(new RecordHeader("traceparent",
                            ("00-" + traceId + "-" + relaySpanId + "-01").getBytes(StandardCharsets.UTF_8)));
                    record.headers().add(new RecordHeader("X-B3-TraceId",
                            traceId.getBytes(StandardCharsets.UTF_8)));
                    record.headers().add(new RecordHeader("X-B3-SpanId",
                            relaySpanId.getBytes(StandardCharsets.UTF_8)));
                    record.headers().add(new RecordHeader("X-B3-Sampled",
                            "1".getBytes(StandardCharsets.UTF_8)));
                }

                kafkaTemplate.send(record).get();
                event.markPublished();
                log.info("Published event {} [{}] to topic {} traceId={}",
                        event.getEventType(), event.getEventId(), topic, traceId);
            } catch (Exception e) {
                log.error("Failed to publish event {} to topic {}: {}", event.getEventId(), topic, e.getMessage());
                event.markFailed();
            }
        }

        outboxRepository.saveAll(pending);
    }
}
