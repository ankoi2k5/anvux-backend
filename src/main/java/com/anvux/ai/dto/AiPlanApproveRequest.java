package com.anvux.ai.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record AiPlanApproveRequest(
        @Size(max = 120, message = "Tên dự án tối đa 120 ký tự")
        String projectName,

        @Valid
        @Size(max = 12, message = "Tối đa 12 công việc")
        List<AiPlanItemInput> items) {
}