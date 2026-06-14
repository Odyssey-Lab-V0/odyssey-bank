package com.banking.iam.infrastructure.persistence.repository;

import com.banking.iam.infrastructure.persistence.entity.IamOutboxEvent;
import com.banking.shared.infrastructure.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<IamOutboxEvent, UUID> {
    List<IamOutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
