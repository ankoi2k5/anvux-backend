package com.anvux.task.dto;

import java.time.Instant;
import java.util.UUID;

import com.anvux.task.TaskPriority;
import com.anvux.task.TaskStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaskUpdateRequest(
        UUID projectId,

        @Pattern(regexp = ".*\\S.*", message = "Tiêu đề không được để trống")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        String title,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        TaskStatus status,

        TaskPriority priority,

        Instant dueAt,

        Boolean clearDueAt,

        @Min(value = 0, message = "Thời gian dự kiến không được âm")
        @Max(value = 100000, message = "Thời gian dự kiến quá lớn")
        Integer estimatedMinutes,

        @Min(value = 0, message = "Thời gian thực tế không được âm")
        @Max(value = 100000, message = "Thời gian thực tế quá lớn")
        Integer actualMinutes) {
}