package com.banking.aml.infrastructure.persistence.entity;

import com.banking.aml.domain.model.AlertStatus;
import com.banking.aml.domain.model.AlertType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aml_alerts", schema = "kyc_aml")
@Getter @Setter @NoArgsConstructor
public class AmlAlertEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
