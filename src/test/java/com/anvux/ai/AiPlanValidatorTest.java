package com.anvux.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.anvux.ai.AiPlanDraft.AiTaskDraft;
import com.anvux.common.ApiException;
import com.anvux.task.TaskPriority;

class AiPlanValidatorTest {

    private final AiPlanValidator validator = new AiPlanValidator();
    private final Instant now = Instant.parse("2026-11-07T02:00:00Z");

    private static AiTaskDraft task(String title, String priority, String dueAt,
                                    Integer minutes, Integer reminder) {
        return new AiTaskDraft(title, null, priority, dueAt, minutes, reminder);
    }

    private static AiPlanDraft draft(AiTaskDraft... tasks) {
        return new AiPlanDraft("Dự án", "Tóm tắt", List.of(tasks));
    }

    @Test
    void validDraft_isAccepted() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Làm bài", "HIGH", "2026-11-10T09:00:00+07:00", 60, 30)), "Dự phòng", now);

        assertEquals(1, plan.items().size());
        AiPlanValidator.ValidatedItem item = plan.items().get(0);
        assertEquals(TaskPriority.HIGH, item.priority());
        assertEquals(Instant.parse("2026-11-10T02:00:00Z"), item.dueAt());
        assertEquals(30, item.reminderMinutesBefore());
    }

    @Test
    void dueDateInThePast_isDropped() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Việc cũ", "LOW", "2026-01-01T09:00:00+07:00", null, null)), "Dự phòng", now);

        assertNull(plan.items().get(0).dueAt());
    }

    @Test
    void dueDateTooFarAway_isDropped() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Việc xa", "LOW", "2035-01-01T09:00:00+07:00", null, null)), "Dự phòng", now);

        assertNull(plan.items().get(0).dueAt());
    }

    @Test
    void malformedDueDate_isDropped() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Việc", "LOW", "ngày mai lúc 9 giờ", null, null)), "Dự phòng", now);

        assertNull(plan.items().get(0).dueAt());
    }

    @Test
    void unknownPriority_defaultsToMedium() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Việc", "URGENT!!!", null, null, null)), "Dự phòng", now);

        assertEquals(TaskPriority.MEDIUM, plan.items().get(0).priority());
    }

    @Test
    void reminderWithoutDueDate_isDropped() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Việc", "LOW", null, null, 30)), "Dự phòng", now);

        assertNull(plan.items().get(0).reminderMinutesBefore());
    }

    @Test
    void moreThanTwelveTasks_areTruncated() {
        AiTaskDraft[] many = IntStream.range(0, 20)
                .mapToObj(i -> task("Việc " + i, "LOW", null, null, null))
                .toArray(AiTaskDraft[]::new);

        assertEquals(12, validator.validate(draft(many), "Dự phòng", now).items().size());
    }

    @Test
    void blankTitles_areSkipped() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("   ", "LOW", null, null, null), task("Việc thật", "LOW", null, null, null)),
                "Dự phòng", now);

        assertEquals(1, plan.items().size());
        assertEquals("Việc thật", plan.items().get(0).title());
    }

    @Test
    void longTitle_isTruncatedTo200Characters() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("a".repeat(300), "LOW", null, null, null)), "Dự phòng", now);

        assertEquals(200, plan.items().get(0).title().length());
    }

    @Test
    void emptyTaskList_isRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> validator.validate(new AiPlanDraft("Dự án", "Tóm tắt", List.of()), "Dự phòng", now));

        assertEquals("AI_EMPTY_PLAN", ex.getCode());
    }

    @Test
    void missingTaskList_isRejectedAsInvalidOutput() {
        ApiException ex = assertThrows(ApiException.class,
                () -> validator.validate(new AiPlanDraft("Dự án", "Tóm tắt", null), "Dự phòng", now));

        assertEquals("AI_INVALID_OUTPUT", ex.getCode());
    }

    @Test
    void missingProjectName_usesFallback() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                new AiPlanDraft(null, "Tóm tắt", List.of(task("Việc", "LOW", null, null, null))),
                "Tên dự phòng", now);

        assertEquals("Tên dự phòng", plan.projectName());
    }

    @Test
    void dueDateWithoutOffset_isReadInUserZone() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Việc", "LOW", "2026-11-10T09:00:00", null, null)), "Dự phòng", now,
                ZoneId.of("Asia/Ho_Chi_Minh"));

        assertEquals(Instant.parse("2026-11-10T02:00:00Z"), plan.items().get(0).dueAt());
    }

    @Test
    void dateOnlyDueDate_defaultsToNineInTheMorning() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Việc", "LOW", "2026-11-10", null, null)), "Dự phòng", now,
                ZoneId.of("Asia/Ho_Chi_Minh"));

        assertEquals(Instant.parse("2026-11-10T02:00:00Z"), plan.items().get(0).dueAt());
    }

    @Test
    void dueDateWithSpaceSeparator_isAccepted() {
        AiPlanValidator.ValidatedPlan plan = validator.validate(
                draft(task("Việc", "LOW", "2026-11-10 09:00", null, null)), "Dự phòng", now,
                ZoneId.of("Asia/Ho_Chi_Minh"));

        assertEquals(Instant.parse("2026-11-10T02:00:00Z"), plan.items().get(0).dueAt());
    }
}
