package com.anvux.ai;

import java.time.Instant;
import java.time.ZoneId;

/** Dữ liệu tối thiểu gửi cho AI: mục tiêu, tên dự án đích (nếu có), múi giờ và thời điểm hiện tại. */
public record AiPlanRequest(String goal, String targetProjectName, ZoneId zone, Instant now) {
}