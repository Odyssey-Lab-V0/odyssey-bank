package com.banking.aml.application;

import com.banking.aml.domain.model.AlertStatus;
import com.banking.aml.domain.model.AlertType;
import com.banking.aml.infrastructure.persistence.entity.AmlAlertEntity;
import com.banking.aml.infrastructure.persistence.repository.AmlAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmlScreeningService {

    private static final BigDecimal LARGE_TX_THRESHOLD  = new BigDecimal("10000");
    private static final BigDecimal STRUCTURING_CEILING = new BigDecimal("9900");
    private static final BigDecimal STRUCTURING_FLOOR   = new BigDecimal("9000");

    private final AmlAlertRepository alertRepository;

    /**
     * Called on every TransactionPosted event.
     * Applies simple rule-based screening — in production replace with ML model.
     */
    @Transactional
    public void screen(UUID accountId, UUID transactionId, BigDecimal amount, String txType) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        // Rule 1: large transaction (CTR threshold)
        if (amount.compareTo(LARGE_TX_THRESHOLD) >= 0) {
            raise(accountId, transactionId, AlertType.LARGE_TRANSACTION, amount,
                    "Transaction of " + amount + " meets or exceeds CTR threshold of $10,000");
        }

        // Rule 2: structuring (just below reporting threshold)
        if (amount.compareTo(STRUCTURING_FLOOR) >= 0 && amount.compareTo(STRUCTURING_CEILING) <= 0) {
            raise(accountId, transactionId, AlertType.STRUCTURING, amount,
                    "Transaction of " + amount + " falls in structuring band $9,000–$9,900");
        }

        // Rule 3: suspiciously round amount > $5,000
        if (amount.compareTo(new BigDecimal("5000")) > 0
                && amount.remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0) {
            raise(accountId, transactionId, AlertType.ROUND_AMOUNT, amount,
                    "Round-number transaction of " + amount + " flagged for review");
        }
    }

    private void raise(UUID accountId, UUID transactionId, AlertType type,
                       BigDecimal amount, String description) {
        AmlAlertEntity alert = new AmlAlertEntity();
        alert.setId(UUID.randomUUID());
        alert.setAccountId(accountId);
        alert.setTransactionId(transactionId);
        alert.setAlertType(type);
        alert.setStatus(AlertStatus.OPEN);
        alert.setAmount(amount);
        alert.setDescription(description);
        alert.setCreatedAt(Instant.now());
        alertRepository.save(alert);

        log.warn("[AML] ALERT {} — accountId={} amount={} — {}",
                type, accountId, amount, description);
    }
}
