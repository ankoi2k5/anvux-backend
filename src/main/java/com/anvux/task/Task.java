package com.anvux.task;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskRecurrence recurrence = TaskRecurrence.NONE;

    @Column(name = "spawned_next_id")
    private UUID spawnedNextId;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
        // JPA cần constructor rỗng
    }

    public Task(UUID userId, UUID projectId, String title) {
        this(null, userId, projectId, title);
    }

    /** id null thì server tự sinh; thiết bị offline-first gửi id của mình lên. */
    public Task(UUID id, UUID userId, UUID projectId, String title) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.projectId = projectId;
        this.title = title.trim();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Đổi trạng thái: sang DONE thì ghi thời điểm hoàn thành, rời khỏi DONE thì xóa. */
    public void changeStatus(TaskStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }
        this.status = newStatus;
        this.completedAt = newStatus == TaskStatus.DONE ? Instant.now() : null;
    }

    public void setTitle(String title) {
        this.title = title.trim();
    }

    public void setDescription(String description) {
        this.description = description == null || description.isBlank() ? null : description.trim();
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public void setActualMinutes(Integer actualMinutes) {
        this.actualMinutes = actualMinutes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public Integer getActualMinutes() {
        return actualMinutes;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public TaskRecurrence getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(TaskRecurrence recurrence) {
        this.recurrence = recurrence;
    }

    public UUID getSpawnedNextId() {
        return spawnedNextId;
    }

    public void setSpawnedNextId(UUID spawnedNextId) {
        this.spawnedNextId = spawnedNextId;
    }
}