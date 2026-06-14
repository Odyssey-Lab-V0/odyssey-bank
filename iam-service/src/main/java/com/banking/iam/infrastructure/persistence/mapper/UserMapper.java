package com.banking.iam.infrastructure.persistence.mapper;

import com.banking.iam.domain.model.User;
import com.banking.iam.domain.valueobject.Email;
import com.banking.iam.domain.valueobject.PhoneNumber;
import com.banking.iam.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Maps between domain aggregate (User) and JPA entity (UserJpaEntity).
 *
 * Manual mapping — deliberately chosen over MapStruct here because:
 *  - Domain VO constructors throw on invalid data; MapStruct won't handle that cleanly
 *  - The mapping is simple enough that MapStruct adds no value
 *  - Explicit mapping makes the translation crystal clear
 */
@Component
public class UserMapper {

    public UserJpaEntity toJpa(User user) {
        return UserJpaEntity.builder()
                .userId(user.getId())
                .email(user.getEmail().value())
                .phoneNumber(user.getPhoneNumber() != null ? user.getPhoneNumber().value() : null)
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .mfaEnabled(user.isMfaEnabled())
                .mfaSecret(user.getMfaSecret())
                .failedAttempts((short) user.getFailedAttempts())
                .lockedUntil(user.getLockedUntil())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .version(user.getVersion())
                .build();
    }

    public User toDomain(UserJpaEntity e) {
        return User.reconstitute(
                e.getUserId(),
                new Email(e.getEmail()),
                e.getPhoneNumber() != null ? new PhoneNumber(e.getPhoneNumber()) : null,
                e.getStatus(),
                e.isEmailVerified(),
                e.isPhoneVerified(),
                e.isMfaEnabled(),
                e.getMfaSecret(),
                (int) e.getFailedAttempts(),
                e.getLockedUntil(),
                e.getLastLoginAt(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion()
        );
    }
}
