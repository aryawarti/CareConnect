package com.careconnect.notification.infrastructure.repository;

import com.careconnect.notification.domain.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
