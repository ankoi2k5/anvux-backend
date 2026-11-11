package com.anvux.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Kế hoạch thô do AI trả về, chưa được kiểm tra. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiPlanDraft(String projectName, String summary, List<AiTaskDraft> tasks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiTaskDraft(
            String title,
            String description,
            String priority,
            String dueAt,
            Integer estimatedMinutes,
            Integer reminderMinutesBefore) {
    }
}