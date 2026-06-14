package com.banking.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "user_roles", schema = "iam")
@Getter
@NoArgsConstructor
@IdClass(UserRoleJpaEntity.UserRoleId.class)
public class UserRoleJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "role_id")
    private UUID roleId;

    public static class UserRoleId implements java.io.Serializable {
        private UUID userId;
        private UUID roleId;
    }
}
