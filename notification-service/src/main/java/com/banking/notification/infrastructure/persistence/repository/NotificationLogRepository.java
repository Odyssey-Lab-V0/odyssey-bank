package com.banking.notification.infrastructure.persistence.repository;

import com.banking.notification.infrastructure.persistence.entity.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {
}
