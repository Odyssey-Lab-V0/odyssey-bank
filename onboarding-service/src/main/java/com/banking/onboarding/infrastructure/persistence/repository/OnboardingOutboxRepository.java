package com.banking.onboarding.infrastructure.persistence.repository;

import com.banking.onboarding.infrastructure.persistence.entity.OnboardingOutboxEvent;
import com.banking.shared.infrastructure.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OnboardingOutboxRepository extends JpaRepository<OnboardingOutboxEvent, UUID> {
    List<OnboardingOutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
