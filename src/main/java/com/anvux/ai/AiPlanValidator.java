package com.anvux.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.anvux.ai.AiPlanDraft.AiTaskDraft;
import com.anvux.common.ApiException;
import com.anvux.task.TaskPriority;

/**
 * Không tin đầu ra của AI: làm sạch từng trường và loại bỏ phần không hợp lệ.
 * Cấu trúc sai hoàn toàn thì từ chối cả kế hoạch, không tạo gì cả.
 */
@Component
public class AiPlanValidator {

    public static final int MAX_ITEMS = 12;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public record ValidatedPlan(String projectName, String summary, List<ValidatedItem> items) {
    }

    public record ValidatedItem(
            String title,
            String description,
            TaskPriority priority,
            Instant dueAt,
            Integer estimatedMinutes,
            Integer reminderMinutesBefore) {
    }

    public ValidatedPlan validate(AiPlanDraft draft, String fallbackProjectName, Instant now) {
        return validate(draft, fallbackProjectName, now, DEFAULT_ZONE);
    }

    /** zone dùng để hiểu hạn chót mà AI viết thiếu múi giờ, ví dụ "2026-11-12T09:00:00" hoặc "2026-11-12". */
    public ValidatedPlan validate(AiPlanDraft draft, String fallbackProjectName, Instant now, ZoneId zone) {
        if (draft == null || draft.tasks() == null) {
            throw AiProviderException.invalidOutput();
        }

        List<ValidatedItem> items = draft.tasks().stream()
                .filter(Objects::nonNull)
                .map(task -> toItem(task, now, zone))
                .filter(Objects::nonNull)
                .limit(MAX_ITEMS)
                .toList();

        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_EMPTY_PLAN",
                    "Không lập được kế hoạch từ yêu cầu này, hãy mô tả rõ hơn mục tiêu của bạn");
        }

        String projectName = cut(clean(draft.projectName()), 120);
        return new ValidatedPlan(
                projectName != null ? projectName : cut(clean(fallbackProjectName), 120),
                cut(clean(draft.summary()), 1000),
                items);
    }

    private ValidatedItem toItem(AiTaskDraft task, Instant now, ZoneId zone) {
        String title = cut(clean(task.title()), 200);
        if (title == null) {
            return null;
        }
        Instant dueAt = parseDue(task.dueAt(), now, zone);
        return new ValidatedItem(
                title,
                cut(clean(task.description()), 2000),
                parsePriority(task.priority()),
                dueAt,
                inRange(task.estimatedMinutes(), 1, 100_000),
                dueAt == null ? null : inRange(task.reminderMinutesBefore(), 1, 10_080));
    }

    private static TaskPriority parsePriority(String value) {
        if (value == null) {
            return TaskPriority.MEDIUM;
        }
        try {
            return TaskPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskPriority.MEDIUM;
        }
    }

    /**
     * Chỉ nhận hạn chót hợp lý: từ một ngày trước đến hai năm sau thời điểm hiện tại.
     * Chấp nhận ISO có múi giờ, ISO thiếu múi giờ (hiểu theo múi giờ người dùng) và chỉ có ngày (lấy 9 giờ sáng).
     */
    private static Instant parseDue(String text, Instant now, ZoneId zone) {
        Instant due = toInstant(text, zone);
        if (due == null) {
            return null;
        }
        boolean sensible = due.isAfter(now.minus(1, ChronoUnit.DAYS))
                && due.isBefore(now.plus(730, ChronoUnit.DAYS));
        return sensible ? due : null;
    }

    private static Instant toInstant(String text, ZoneId zone) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        if (value.length() > 10 && value.charAt(10) == ' ') {
            value = value.substring(0, 10) + "T" + value.substring(11);
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // thử dạng thiếu múi giờ
        }
        try {
            return LocalDateTime.parse(value).atZone(zone).toInstant();
        } catch (DateTimeParseException ignored) {
            // thử dạng chỉ có ngày
        }
        try {
            return LocalDate.parse(value).atTime(9, 0).atZone(zone).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Integer inRange(Integer value, int min, int max) {
        return value != null && value >= min && value <= max ? value : null;
    }

    /** Bỏ ký tự điều khiển (trừ xuống dòng, tab), cắt khoảng trắng hai đầu; rỗng thì trả về null. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max).trim();
    }
}