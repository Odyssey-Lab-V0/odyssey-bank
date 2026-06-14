package com.banking.onboarding.application.command;

import com.banking.onboarding.domain.event.CustomerOnboarded;
import com.banking.onboarding.domain.model.Customer;
import com.banking.onboarding.infrastructure.persistence.entity.CustomerJpaEntity;
import com.banking.onboarding.infrastructure.persistence.entity.OnboardingOutboxEvent;
import com.banking.onboarding.infrastructure.persistence.repository.CustomerJpaRepository;
import com.banking.onboarding.infrastructure.persistence.repository.OnboardingOutboxRepository;
import com.banking.shared.domain.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingApplicationService {

    private final CustomerJpaRepository customerRepository;
    private final OnboardingOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Called when IAM publishes UserRegistered.
     * Creates a Customer record and queues CustomerOnboarded in the outbox — same transaction.
     */
    @Transactional
    public void initiateOnboarding(UUID userId, String email, String firstName, String lastName) {
        if (customerRepository.existsByUserId(userId)) {
            log.warn("Onboarding already initiated for userId={} — skipping (idempotent)", userId);
            return;
        }

        Customer customer = Customer.create(userId, email, firstName, lastName);

        CustomerJpaEntity entity = toEntity(customer);
        customerRepository.save(entity);

        drainEventsToOutbox(customer);

        log.info("Onboarding initiated for userId={} customerId={}", userId, customer.getId());
    }

    private void drainEventsToOutbox(Customer customer) {
        for (DomainEvent event : customer.drainEvents()) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                outboxRepository.save(new OnboardingOutboxEvent(
                        event.eventId(),
                        event.aggregateType(),
                        event.aggregateId(),
                        event.eventType(),
                        payload
                ));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize domain event: " + event.eventType(), e);
            }
        }
    }

    private CustomerJpaEntity toEntity(Customer c) {
        CustomerJpaEntity e = new CustomerJpaEntity();
        e.setId(c.getId());
        e.setUserId(c.getUserId());
        e.setEmail(c.getEmail());
        e.setFirstName(c.getFirstName());
        e.setLastName(c.getLastName());
        e.setStatus(c.getStatus());
        e.setCreatedAt(c.getCreatedAt());
        return e;
    }
}
