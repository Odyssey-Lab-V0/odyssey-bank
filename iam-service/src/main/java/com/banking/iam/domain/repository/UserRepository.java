package com.banking.iam.domain.repository;

import com.banking.iam.domain.model.User;
import com.banking.iam.domain.valueobject.Email;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository interface — defined in the domain layer, implemented in infrastructure.
 *
 * This is the Dependency Inversion Principle in DDD: the domain defines what it needs,
 * the infrastructure provides it. The domain never depends on JPA or Spring directly.
 *
 * The application layer calls this interface. Spring wires in the JPA implementation.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID userId);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
