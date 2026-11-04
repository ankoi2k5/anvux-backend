package com.anvux.reminder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReminderRepository extends JpaRepository<Reminder, UUID>, JpaSpecificationExecutor<Reminder> {

    Optional<Reminder> findByIdAndUserId(UUID id, UUID userId);

    long countByTaskId(UUID taskId);

    boolean existsByTaskIdAndRemindAt(UUID taskId, Instant remindAt);
}