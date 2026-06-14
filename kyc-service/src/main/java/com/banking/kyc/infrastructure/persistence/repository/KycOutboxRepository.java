package com.banking.kyc.infrastructure.persistence.repository;

import com.banking.kyc.infrastructure.persistence.entity.KycOutboxEvent;
import com.banking.shared.infrastructure.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycOutboxRepository extends JpaRepository<KycOutboxEvent, UUID> {
    List<KycOutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
