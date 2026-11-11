package com.anvux.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.anvux.ai.AiPlan;
import com.anvux.ai.AiPlanItem;
import com.anvux.ai.AiPlanStatus;
import com.anvux.task.TaskPriority;

public record AiPlanResponse(
        UUID id,
        String prompt,
        AiPlanStatus status,
        String summary,
        String projectName,
        UUID targetProjectId,
        UUID createdProjectId,
        String provider,
        String model,
        List<Item> items,
        Instant createdAt,
        Instant executedAt) {

    public record Item(
            UUID id,
            int order,
            String title,
            String description,
            TaskPriority priority,
            Instant dueAt,
            Integer estimatedMinutes,
            Integer reminderMinutesBefore,
            UUID createdTaskId) {

        static Item from(AiPlanItem item) {
            return new Item(
                    item.getId(),
                    item.getSortOrder(),
                    item.getTitle(),
                    item.getDescription(),
                    item.getPriority(),
                    item.getDueAt(),
                    item.getEstimatedMinutes(),
                    item.getReminderMinutesBefore(),
                    item.getCreatedTaskId());
        }
    }

    public static AiPlanResponse from(AiPlan plan) {
        return new AiPlanResponse(
                plan.getId(),
                plan.getPrompt(),
                plan.getStatus(),
                plan.getSummary(),
                plan.getProjectName(),
                plan.getTargetProjectId(),
                plan.getCreatedProjectId(),
                plan.getProvider(),
                plan.getModel(),
                plan.getItems().stream().map(Item::from).toList(),
                plan.getCreatedAt(),
                plan.getExecutedAt());
    }
}