package com.anvux.reminder;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Column(nullable = false)
    private boolean enabled = true;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reminder() {
        // JPA cần constructor rỗng
    }

    public Reminder(UUID userId, UUID taskId, Instant remindAt) {
        this(null, userId, taskId, remindAt);
    }

    public Reminder(UUID id, UUID userId, UUID taskId, Instant remindAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.taskId = taskId;
        this.remindAt = remindAt;
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

    public void setRemindAt(Instant remindAt) {
        this.remindAt = remindAt;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public boolean isEnabled() {
        return enabled;
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
}