package com.banking.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "roles", schema = "iam")
@Getter
@NoArgsConstructor
public class RoleJpaEntity {

    @Id
    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "description")
    private String description;
}
