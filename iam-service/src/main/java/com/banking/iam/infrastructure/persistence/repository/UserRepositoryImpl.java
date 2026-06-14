package com.banking.iam.infrastructure.persistence.repository;

import com.banking.iam.domain.model.User;
import com.banking.iam.domain.repository.UserRepository;
import com.banking.iam.domain.valueobject.Email;
import com.banking.iam.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of the domain UserRepository interface.
 *
 * This is the Adapter in Hexagonal Architecture — it adapts the Spring Data JPA
 * port to the domain's repository contract.
 *
 * The domain only sees UserRepository (the port).
 * Spring wires this implementation in automatically.
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;

    @Override
    public User save(User user) {
        var jpaEntity = mapper.toJpa(user);
        var saved = jpaRepository.save(jpaEntity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return jpaRepository.findById(userId).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }
}
