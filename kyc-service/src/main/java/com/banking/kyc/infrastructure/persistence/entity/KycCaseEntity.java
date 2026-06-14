package com.banking.kyc.infrastructure.persistence.entity;

import com.banking.kyc.domain.model.KycStatus;
import com.banking.kyc.domain.model.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_cases", schema = "kyc_aml")
@Getter @Setter @NoArgsConstructor
public class KycCaseEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 10)
    private RiskLevel riskLevel;

    @Column(name = "risk_score")
    private Integer riskScore;      // 0-100, computed on approval

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
