package com.anvux.task.dto;

import java.time.Instant;
import java.util.UUID;

import com.anvux.task.Task;
import com.anvux.task.TaskPriority;
import com.anvux.task.TaskRecurrence;
import com.anvux.task.TaskStatus;

public record TaskResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueAt,
        Instant completedAt,
        Integer estimatedMinutes,
        Integer actualMinutes,
        TaskRecurrence recurrence,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueAt(),
                task.getCompletedAt(),
                task.getEstimatedMinutes(),
                task.getActualMinutes(),
                task.getRecurrence(),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}