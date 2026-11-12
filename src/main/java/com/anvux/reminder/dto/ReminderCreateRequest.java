package com.anvux.reminder.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ReminderCreateRequest(
        UUID id,
        @NotNull(message = "Thiếu công việc")
        UUID taskId,

        @NotNull(message = "Vui lòng chọn thời điểm nhắc")
        Instant remindAt) {
}