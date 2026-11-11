package com.anvux.ai.dto;

import java.time.Instant;

import com.anvux.task.TaskPriority;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiPlanItemInput(
        @NotBlank(message = "Vui lòng nhập tiêu đề")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        String title,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        TaskPriority priority,

        Instant dueAt,

        @Min(value = 1, message = "Thời gian dự kiến tối thiểu 1 phút")
        @Max(value = 100000, message = "Thời gian dự kiến quá lớn")
        Integer estimatedMinutes,

        @Min(value = 1, message = "Nhắc trước tối thiểu 1 phút")
        @Max(value = 10080, message = "Nhắc trước tối đa 7 ngày")
        Integer reminderMinutesBefore) {
}