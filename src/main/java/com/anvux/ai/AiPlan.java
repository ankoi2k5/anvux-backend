package com.anvux.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "ai_plans")
public class AiPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 2000, updatable = false)
    private String prompt;

    @Column(name = "target_project_id")
    private UUID targetProjectId;

    @Column(name = "project_name", length = 120)
    private String projectName;

    @Column(length = 1000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiPlanStatus status = AiPlanStatus.DRAFT;

    @Column(nullable = false, length = 30, updatable = false)
    private String provider;

    @Column(length = 60, updatable = false)
    private String model;

    @Column(name = "created_project_id")
    private UUID createdProjectId;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<AiPlanItem> items = new ArrayList<>();

    protected AiPlan() {
        // JPA cần constructor rỗng
    }

    public AiPlan(UUID userId, String prompt, UUID targetProjectId, String projectName,
                  String summary, String provider, String model) {
        this.userId = userId;
        this.prompt = prompt;
        this.targetProjectId = targetProjectId;
        this.projectName = projectName;
        this.summary = summary;
        this.provider = provider;
        this.model = model;
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

    public void addItem(AiPlanItem item) {
        item.setPlan(this);
        items.add(item);
    }

    public void replaceItems(List<AiPlanItem> newItems) {
        items.clear();
        newItems.forEach(this::addItem);
    }

    public void markExecuted() {
        this.status = AiPlanStatus.EXECUTED;
        this.executedAt = Instant.now();
    }

    public void reject() {
        this.status = AiPlanStatus.REJECTED;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setCreatedProjectId(UUID createdProjectId) {
        this.createdProjectId = createdProjectId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPrompt() {
        return prompt;
    }

    public UUID getTargetProjectId() {
        return targetProjectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getSummary() {
        return summary;
    }

    public AiPlanStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public UUID getCreatedProjectId() {
        return createdProjectId;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<AiPlanItem> getItems() {
        return items;
    }
}