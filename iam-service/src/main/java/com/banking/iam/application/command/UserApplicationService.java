package com.banking.iam.application.command;

import com.banking.iam.domain.model.User;
import com.banking.iam.domain.repository.UserRepository;
import com.banking.iam.domain.valueobject.Email;
import com.banking.iam.domain.valueobject.HashedPassword;
import com.banking.iam.domain.valueobject.PhoneNumber;
import com.banking.iam.infrastructure.persistence.entity.CredentialJpaEntity;
import com.banking.iam.infrastructure.persistence.repository.CredentialJpaRepository;
import com.banking.iam.infrastructure.persistence.repository.OutboxJpaRepository;
import com.banking.shared.domain.exception.BusinessRuleViolationException;
import com.banking.shared.infrastructure.outbox.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application Service for User write operations.
 *
 * Application services coordinate:
 *   1. Load aggregate from repository
 *   2. Call domain method (business logic lives in the aggregate)
 *   3. Save aggregate (triggers persistence)
 *   4. Write domain events to outbox (in same transaction)
 *
 * They do NOT contain business logic themselves — that belongs in the domain.
 * If you find yourself writing an if-statement about a business rule here, move it to User.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final UserRepository userRepository;
    private final CredentialJpaRepository credentialRepository;
    private final OutboxJpaRepository outboxRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final ObservationRegistry observationRegistry;

    @Transactional
    public UUID registerUser(RegisterUserCommand cmd) {
        var email = new Email(cmd.email());
        var phone = new PhoneNumber(cmd.phoneNumber());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleViolationException("EMAIL_ALREADY_REGISTERED",
                    "An account with this email already exists");
        }

        // 1. Create aggregate via factory method — raises UserRegistered event internally
        var user = User.register(email, phone);

        // 2. Hash password — done in application layer (not domain, not infra)
        //    Domain knows about HashedPassword VO but not about BCrypt implementation
        var hashed = passwordEncoder.encode(cmd.password());

        // 3. Save user aggregate — repository maps domain → JPA entity
        var savedUser = userRepository.save(user);

        // 4. Save credentials (separate entity, same transaction)
        var credential = new CredentialJpaEntity(savedUser.getId(), hashed);
        credentialRepository.save(credential);

        // 5. Drain domain events → write to outbox (same DB transaction)
        //    Kafka relay will pick these up asynchronously
        user.pullDomainEvents().forEach(event -> {
            try {
                var outbox = new com.banking.iam.infrastructure.persistence.entity.IamOutboxEvent(
                        event.eventId(), event.aggregateType(), event.aggregateId(),
                        event.eventType(), objectMapper.writeValueAsString(event),
                        currentTraceId()
                );
                outboxRepository.save(outbox);
            } catch (Exception e) {
                throw new RuntimeException("Failed to write domain event to outbox", e);
            }
        });

        log.info("User registered: userId={}, email={}", savedUser.getId(), email.value());
        return savedUser.getId();
    }

    @Transactional
    public void verifyEmail(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("USER_NOT_FOUND",
                        "User not found: " + userId));

        user.verifyEmail(); // domain method — may raise UserStatusChanged event
        userRepository.save(user);

        // drain and persist events
        persistEvents(user);
    }

    @Transactional
    public void recordFailedLogin(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleViolationException("USER_NOT_FOUND", "User not found"));
        user.recordFailedLogin();
        userRepository.save(user);
        persistEvents(user);
    }

    private String currentTraceId() {
        // In Spring Boot 3.x, HTTP spans are managed via ObservationRegistry, not directly via Tracer.
        // The TracingObservationHandler bridges the two, so we pull the span from the active observation.
        var obs = observationRegistry.getCurrentObservation();
        if (obs != null) {
            TracingObservationHandler.TracingContext tracingCtx =
                    obs.getContextView().get(TracingObservationHandler.TracingContext.class);
            if (tracingCtx != null && tracingCtx.getSpan() != null) {
                String id = tracingCtx.getSpan().context().traceId();
                log.debug("Captured traceId from ObservationRegistry: {}", id);
                return id;
            }
        }
        String mdcId = org.slf4j.MDC.get("traceId");
        if (mdcId != null && !mdcId.isBlank()) return mdcId;
        var span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }

    private void persistEvents(User user) {
        user.pullDomainEvents().forEach(event -> {
            try {
                var outbox = new com.banking.iam.infrastructure.persistence.entity.IamOutboxEvent(
                        event.eventId(), event.aggregateType(), event.aggregateId(),
                        event.eventType(), objectMapper.writeValueAsString(event),
                        currentTraceId()
                );
                outboxRepository.save(outbox);
            } catch (Exception e) {
                throw new RuntimeException("Outbox write failed", e);
            }
        });
    }
}
