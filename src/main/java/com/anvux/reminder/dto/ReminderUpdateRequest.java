package com.anvux.reminder.dto;

import java.time.Instant;

public record ReminderUpdateRequest(
        Instant remindAt,
        Boolean enabled) {
}