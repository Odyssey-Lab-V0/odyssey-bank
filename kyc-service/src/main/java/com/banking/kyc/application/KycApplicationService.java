package com.banking.kyc.application;

import com.banking.kyc.domain.model.KycStatus;
import com.banking.kyc.domain.model.RiskLevel;
import com.banking.kyc.infrastructure.persistence.entity.KycCaseEntity;
import com.banking.kyc.infrastructure.persistence.entity.KycOutboxEvent;
import com.banking.kyc.infrastructure.persistence.repository.KycCaseRepository;
import com.banking.kyc.infrastructure.persistence.repository.KycOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycApplicationService {

    private final KycCaseRepository caseRepository;
    private final KycOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Called when Onboarding publishes CustomerOnboarded.
     * Creates a KYC case and auto-approves (simulated — in production, human review or 3rd-party API).
     */
    @Transactional
    public void initiateKyc(UUID customerId, UUID userId, String email, String fullName) {
        if (caseRepository.existsByCustomerId(customerId)) {
            log.warn("KYC case already exists for customerId={} — skipping", customerId);
            return;
        }

        // Simulate risk scoring: assign LOW by default in dev
        int riskScore = simulateRiskScore(email);
        RiskLevel riskLevel = riskScore < 30 ? RiskLevel.LOW
                            : riskScore < 70 ? RiskLevel.MEDIUM
                            : RiskLevel.HIGH;

        KycCaseEntity kycCase = new KycCaseEntity();
        kycCase.setId(UUID.randomUUID());
        kycCase.setCustomerId(customerId);
        kycCase.setUserId(userId);
        kycCase.setEmail(email);
        kycCase.setStatus(KycStatus.IN_REVIEW);
        kycCase.setRiskScore(riskScore);
        kycCase.setRiskLevel(riskLevel);
        kycCase.setCreatedAt(Instant.now());
        caseRepository.save(kycCase);

        // Auto-approve in dev (no document upload required)
        kycCase.setStatus(KycStatus.APPROVED);
        kycCase.setReviewedAt(Instant.now());
        kycCase.setReviewerNotes("Auto-approved in development mode");
        caseRepository.save(kycCase);

        // Publish KYCApproved to outbox
        publishToOutbox(kycCase, fullName);

        log.info("[KYC] Case created and approved for customerId={} riskLevel={} score={}",
                customerId, riskLevel, riskScore);
    }

    private void publishToOutbox(KycCaseEntity kycCase, String fullName) {
        try {
            Map<String, Object> payload = Map.of(
                    "kycCaseId",   kycCase.getId().toString(),
                    "customerId",  kycCase.getCustomerId().toString(),
                    "userId",      kycCase.getUserId().toString(),
                    "email",       kycCase.getEmail(),
                    "fullName",    fullName != null ? fullName : "",
                    "riskLevel",   kycCase.getRiskLevel().name(),
                    "riskScore",   kycCase.getRiskScore(),
                    "approvedAt",  kycCase.getReviewedAt().toString()
            );
            outboxRepository.save(new KycOutboxEvent(
                    UUID.randomUUID(), "KycCase", kycCase.getId(),
                    "KYCApproved", objectMapper.writeValueAsString(payload)
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize KYCApproved event", e);
        }
    }

    private int simulateRiskScore(String email) {
        // Deterministic but fake — real scoring uses sanctions lists, PEP databases
        return Math.abs(email.hashCode() % 30); // always LOW in dev
    }
}
