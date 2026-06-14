package com.banking.notification.application;

import com.banking.notification.domain.model.NotificationChannel;
import com.banking.notification.domain.model.NotificationStatus;
import com.banking.notification.infrastructure.persistence.entity.NotificationLogEntity;
import com.banking.notification.infrastructure.persistence.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationLogRepository logRepository;

    @Transactional
    public void send(UUID recipientId, String recipientEmail,
                     String subject, String body, String triggerEvent) {
        // In production: call SendGrid / AWS SES here.
        // For now: simulate and log.
        log.info("📧 [NOTIFICATION] TO={} SUBJECT=\"{}\" TRIGGER={}", recipientEmail, subject, triggerEvent);
        log.debug("   BODY: {}", body);

        NotificationLogEntity entry = new NotificationLogEntity();
        entry.setId(UUID.randomUUID());
        entry.setRecipientId(recipientId);
        entry.setRecipient(recipientEmail);
        entry.setChannel(NotificationChannel.EMAIL);
        entry.setSubject(subject);
        entry.setBody(body);
        entry.setTriggerEvent(triggerEvent);
        entry.setStatus(NotificationStatus.SENT);
        entry.setSentAt(Instant.now());

        logRepository.save(entry);
    }
}
