package com.banking.notification.infrastructure.persistence.entity;

import com.banking.notification.domain.model.NotificationChannel;
import com.banking.notification.domain.model.NotificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log", schema = "notification")
@Getter @Setter @NoArgsConstructor
public class NotificationLogEntity {

    @Id
    private UUID id;

    @Column(name = "recipient_id")
    private UUID recipientId;       // userId or customerId from event

    @Column(nullable = false)
    private String recipient;       // email address or phone

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "trigger_event", nullable = false)
    private String triggerEvent;    // e.g. "CustomerOnboarded", "TransactionPosted"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
