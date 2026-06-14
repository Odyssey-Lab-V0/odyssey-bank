package com.banking.iam.domain;

import com.banking.iam.domain.event.UserRegistered;
import com.banking.iam.domain.event.UserStatusChanged;
import com.banking.iam.domain.model.User;
import com.banking.iam.domain.model.UserStatus;
import com.banking.iam.domain.valueobject.Email;
import com.banking.iam.domain.valueobject.PhoneNumber;
import com.banking.shared.domain.exception.BusinessRuleViolationException;
import com.banking.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure domain unit tests — no Spring context, no database, no mocks.
 * Tests MUST run in milliseconds. If you need a mock here, that's a sign
 * that business logic leaked into the wrong layer.
 */
class UserTest {

    private User newUser() {
        return User.register(
                new Email("john.doe@example.com"),
                new PhoneNumber("+12125551234")
        );
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        void registers_with_pending_status() {
            var user = newUser();
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(user.isEmailVerified()).isFalse();
        }

        @Test
        void raises_UserRegistered_event_on_creation() {
            var user = newUser();
            var events = user.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(UserRegistered.class);
            assertThat(((UserRegistered) events.get(0)).getEmail()).isEqualTo("john.doe@example.com");
        }

        @Test
        void pulling_events_clears_them() {
            var user = newUser();
            user.pullDomainEvents(); // drain
            assertThat(user.pullDomainEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Email verification")
    class EmailVerification {

        @Test
        void activates_user_on_email_verify() {
            var user = newUser();
            user.pullDomainEvents(); // drain registration event

            user.verifyEmail();

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isEmailVerified()).isTrue();
        }

        @Test
        void raises_StatusChanged_event_on_activation() {
            var user = newUser();
            user.pullDomainEvents();
            user.verifyEmail();

            var events = user.pullDomainEvents();
            assertThat(events).hasSize(1);
            var statusChanged = (UserStatusChanged) events.get(0);
            assertThat(statusChanged.getPreviousStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(statusChanged.getNewStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Login protection")
    class LoginProtection {

        @Test
        void locks_account_after_5_failed_attempts() {
            var user = newUser();
            user.verifyEmail(); // activate

            for (int i = 0; i < 5; i++) {
                user.recordFailedLogin();
            }

            assertThat(user.isLocked()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        }

        @Test
        void locked_user_cannot_login() {
            var user = newUser();
            user.verifyEmail();
            for (int i = 0; i < 5; i++) user.recordFailedLogin();

            assertThatThrownBy(user::recordSuccessfulLogin)
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("locked");
        }

        @Test
        void unlock_resets_failed_attempts() {
            var user = newUser();
            user.verifyEmail();
            for (int i = 0; i < 5; i++) user.recordFailedLogin();

            user.unlock();

            assertThat(user.isLocked()).isFalse();
            assertThat(user.getFailedAttempts()).isZero();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Value Objects")
    class ValueObjects {

        @Test
        void email_normalises_to_lowercase() {
            var email = new Email("John.DOE@Example.COM");
            assertThat(email.value()).isEqualTo("john.doe@example.com");
        }

        @Test
        void email_rejects_invalid_format() {
            assertThatThrownBy(() -> new Email("not-an-email"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("invalid");
        }

        @Test
        void phone_rejects_non_e164() {
            assertThatThrownBy(() -> new PhoneNumber("12125551234")) // missing +
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void email_equality_is_value_based() {
            assertThat(new Email("test@example.com")).isEqualTo(new Email("TEST@EXAMPLE.COM"));
        }
    }
}
