package com.banking.kyc.infrastructure.messaging;

import com.banking.kyc.infrastructure.persistence.entity.KycOutboxEvent;
import com.banking.kyc.infrastructure.persistence.repository.KycOutboxRepository;
import com.banking.shared.infrastructure.outbox.OutboxEvent.OutboxStatus;
import io.micrometer.tracing.ScopedSpan;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class KycOutboxRelayService {

    private static final Map<String, String> TOPIC_MAP = Map.of(
            "KYCApproved", "banking.kyc.approved.v1",
            "KYCRejected", "banking.kyc.rejected.v1"
    );

    private final KycOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Tracer tracer;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relay() {
        List<KycOutboxEvent> pending = outboxRepository
                .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) return;

        for (KycOutboxEvent event : pending) {
            String topic = TOPIC_MAP.getOrDefault(event.getEventType(),
                    "banking.kyc." + event.getEventType().toLowerCase() + ".v1");

            ScopedSpan span = tracer.startScopedSpan("outbox.relay " + event.getEventType());
            span.tag("messaging.destination", topic);
            span.tag("event.id", event.getEventId().toString());
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, null, event.getAggregateId().toString(), event.getPayload());

                String traceId = tracer.currentSpan().context().traceId();
                String spanId  = tracer.currentSpan().context().spanId();
                record.headers().add(new RecordHeader("traceparent",
                        ("00-" + traceId + "-" + spanId + "-01").getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("X-B3-TraceId",  traceId.getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("X-B3-SpanId",   spanId.getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("X-B3-Sampled",  "1".getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).get();
                event.markPublished();
                span.end();
                log.info("Published {} [{}] → {}", event.getEventType(), event.getEventId(), topic);
            } catch (Exception e) {
                span.error(e);
                span.end();
                log.error("Failed to publish KYC event {}: {}", event.getEventId(), e.getMessage());
                event.markFailed();
            }
        }
        outboxRepository.saveAll(pending);
    }
}
