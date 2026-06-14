package com.banking.core.api.rest;

import com.banking.core.application.command.AccountApplicationService;
import com.banking.core.domain.model.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountApplicationService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest req) {
        var account = accountService.openAccount(
                req.customerId(), req.accountType(), req.currency(), req.initialDeposit());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("accountId") UUID accountId) {
        return ResponseEntity.ok(AccountResponse.from(accountService.getAccount(accountId)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountResponse>> getByCustomer(@PathVariable("customerId") UUID customerId) {
        return ResponseEntity.ok(accountService.getAccountsByCustomer(customerId)
                .stream().map(AccountResponse::from).toList());
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<Void> debit(@PathVariable("accountId") UUID accountId,
                                      @Valid @RequestBody TransactionRequest req) {
        var idempotencyKey = UUID.fromString(
                req.idempotencyKey() != null ? req.idempotencyKey() : UUID.randomUUID().toString());
        accountService.debit(accountId, req.amount(), req.currency(), req.reference(), idempotencyKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<Void> credit(@PathVariable("accountId") UUID accountId,
                                       @Valid @RequestBody TransactionRequest req) {
        var idempotencyKey = UUID.fromString(
                req.idempotencyKey() != null ? req.idempotencyKey() : UUID.randomUUID().toString());
        accountService.credit(accountId, req.amount(), req.currency(), req.reference(), idempotencyKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferRequest req) {
        accountService.transfer(req.fromAccountId(), req.toAccountId(),
                req.amount(), req.currency(), req.reference());
        return ResponseEntity.ok().build();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record OpenAccountRequest(
            @NotNull UUID customerId,
            @NotBlank String accountType,
            @NotBlank String currency,
            BigDecimal initialDeposit
    ) {}

    public record TransactionRequest(
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String reference,
            String idempotencyKey
    ) {}

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String reference
    ) {}

    public record AccountResponse(
            UUID accountId,
            UUID customerId,
            String accountNumber,
            String accountType,
            String status,
            String currency,
            BigDecimal balance
    ) {
        static AccountResponse from(Account a) {
            return new AccountResponse(
                    a.getId(), a.getCustomerId(),
                    a.getAccountNumber().value(),
                    a.getAccountType().name(),
                    a.getStatus().name(),
                    a.getCurrency(),
                    a.getBalance().amount()
            );
        }
    }
}
