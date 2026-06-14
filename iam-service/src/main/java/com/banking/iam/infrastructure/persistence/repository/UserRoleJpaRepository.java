package com.banking.iam.infrastructure.persistence.repository;

import com.banking.iam.infrastructure.persistence.entity.UserRoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRoleJpaRepository extends JpaRepository<UserRoleJpaEntity, Long> {

    @Query("""
            SELECT r.name FROM UserRoleJpaEntity ur
            JOIN RoleJpaEntity r ON r.roleId = ur.roleId
            WHERE ur.userId = :userId
            """)
    List<String> findRoleNamesByUserId(@Param("userId") UUID userId);
}
