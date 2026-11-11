package com.anvux.ai;

import java.time.Instant;
import java.util.UUID;

import com.anvux.task.TaskPriority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_plan_items")
public class AiPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private AiPlan plan;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Danh sách hành động được phép hiện chỉ có CREATE_TASK (cũng được khóa bằng ràng buộc ở cơ sở dữ liệu). */
    @Column(nullable = false, length = 20, updatable = false)
    private String action = "CREATE_TASK";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "reminder_minutes_before")
    private Integer reminderMinutesBefore;

    @Column(name = "created_task_id")
    private UUID createdTaskId;

    protected AiPlanItem() {
        // JPA cần constructor rỗng
    }

    public AiPlanItem(int sortOrder, String title, String description, TaskPriority priority,
                      Instant dueAt, Integer estimatedMinutes, Integer reminderMinutesBefore) {
        this.sortOrder = sortOrder;
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.priority = priority == null ? TaskPriority.MEDIUM : priority;
        this.dueAt = dueAt;
        this.estimatedMinutes = estimatedMinutes;
        this.reminderMinutesBefore = reminderMinutesBefore;
    }

    void setPlan(AiPlan plan) {
        this.plan = plan;
    }

    public void setCreatedTaskId(UUID createdTaskId) {
        this.createdTaskId = createdTaskId;
    }

    public UUID getId() {
        return id;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public Integer getReminderMinutesBefore() {
        return reminderMinutesBefore;
    }

    public UUID getCreatedTaskId() {
        return createdTaskId;
    }
}