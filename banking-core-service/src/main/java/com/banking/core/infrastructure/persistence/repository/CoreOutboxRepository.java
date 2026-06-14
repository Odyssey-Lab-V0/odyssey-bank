package com.banking.core.infrastructure.persistence.repository;

import com.banking.core.infrastructure.persistence.entity.CoreOutboxEvent;
import com.banking.shared.infrastructure.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoreOutboxRepository extends JpaRepository<CoreOutboxEvent, UUID> {
    List<CoreOutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
