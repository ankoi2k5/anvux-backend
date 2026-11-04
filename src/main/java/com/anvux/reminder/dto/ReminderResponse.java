package com.anvux.reminder.dto;

import java.time.Instant;
import java.util.UUID;

import com.anvux.reminder.Reminder;

public record ReminderResponse(
        UUID id,
        UUID taskId,
        Instant remindAt,
        boolean enabled,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public static ReminderResponse from(Reminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getTaskId(),
                reminder.getRemindAt(),
                reminder.isEnabled(),
                reminder.getVersion(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt());
    }
}