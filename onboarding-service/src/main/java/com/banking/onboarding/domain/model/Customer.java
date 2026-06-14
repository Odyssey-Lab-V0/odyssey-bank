package com.banking.onboarding.domain.model;

import com.banking.onboarding.domain.event.CustomerOnboarded;
import com.banking.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Customer {

    private final UUID id;
    private final UUID userId;          // reference to iam.users — never join across schemas
    private final String email;
    private String firstName;
    private String lastName;
    private OnboardingStatus status;
    private final Instant createdAt;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Customer(UUID id, UUID userId, String email,
                     String firstName, String lastName, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = OnboardingStatus.PENDING_KYC;
        this.createdAt = createdAt;
    }

    public static Customer create(UUID userId, String email, String firstName, String lastName) {
        Customer c = new Customer(UUID.randomUUID(), userId, email, firstName, lastName, Instant.now());
        c.domainEvents.add(new CustomerOnboarded(
                UUID.randomUUID(), c.id, userId, email,
                firstName + " " + lastName, c.createdAt));
        return c;
    }

    public void approveKyc() {
        if (status != OnboardingStatus.KYC_SUBMITTED) {
            throw new IllegalStateException("KYC not submitted yet");
        }
        this.status = OnboardingStatus.KYC_APPROVED;
    }

    public void rejectKyc(String reason) {
        this.status = OnboardingStatus.KYC_REJECTED;
    }

    public void complete() {
        if (status != OnboardingStatus.KYC_APPROVED) {
            throw new IllegalStateException("KYC must be approved before completing onboarding");
        }
        this.status = OnboardingStatus.COMPLETED;
    }

    public List<DomainEvent> drainEvents() {
        List<DomainEvent> copy = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return Collections.unmodifiableList(copy);
    }

    public UUID getId()          { return id; }
    public UUID getUserId()      { return userId; }
    public String getEmail()     { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName()  { return lastName; }
    public OnboardingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
