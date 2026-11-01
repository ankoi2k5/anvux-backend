package com.anvux.task.dto;

import java.time.Instant;
import java.util.UUID;

import com.anvux.task.TaskPriority;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskCreateRequest(
        @NotNull(message = "Thiếu dự án")
        UUID projectId,

        @NotBlank(message = "Vui lòng nhập tiêu đề")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        String title,

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        String description,

        TaskPriority priority,

        Instant dueAt,

        @Min(value = 0, message = "Thời gian dự kiến không được âm")
        @Max(value = 100000, message = "Thời gian dự kiến quá lớn")
        Integer estimatedMinutes) {
}