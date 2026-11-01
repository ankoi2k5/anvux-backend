package com.anvux.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvux.common.CreateResult;
import com.anvux.common.IdempotencyService;
import com.anvux.common.ResourceNotFoundException;
import com.anvux.project.ProjectRepository;
import com.anvux.task.dto.TaskCreateRequest;
import com.anvux.task.dto.TaskResponse;
import com.anvux.task.dto.TaskUpdateRequest;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final int MAX_RESULTS = 500;
    private static final String RESOURCE_TYPE = "TASK";

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final IdempotencyService idempotency;

    public TaskService(TaskRepository tasks, ProjectRepository projects, IdempotencyService idempotency) {
        this.tasks = tasks;
        this.projects = projects;
        this.idempotency = idempotency;
    }

    public List<TaskResponse> list(UUID userId,
                                   UUID projectId,
                                   TaskStatus status,
                                   TaskPriority priority,
                                   Instant dueAfter,
                                   Instant dueBefore) {
        Sort sort = Sort.by(Sort.Order.asc("dueAt"), Sort.Order.desc("createdAt"));
        return tasks.findAll(
                        TaskSpecifications.filter(userId, projectId, status, priority, dueAfter, dueBefore),
                        PageRequest.of(0, MAX_RESULTS, sort))
                .getContent().stream()
                .map(TaskResponse::from)
                .toList();
    }

    public TaskResponse get(UUID userId, UUID id) {
        return TaskResponse.from(find(userId, id));
    }

    @Transactional
    public CreateResult<TaskResponse> create(UUID userId, String idempotencyKey, TaskCreateRequest request) {
        String key = idempotency.validate(idempotencyKey);

        if (key != null) {
            Optional<Task> existing = idempotency.findResourceId(userId, key, RESOURCE_TYPE)
                    .flatMap(id -> tasks.findByIdAndUserId(id, userId));
            if (existing.isPresent()) {
                return new CreateResult<>(TaskResponse.from(existing.get()), false);
            }
        }

        requireOwnedProject(userId, request.projectId());

        Task task = new Task(userId, request.projectId(), request.title());
        task.setDescription(request.description());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setDueAt(request.dueAt());
        task.setEstimatedMinutes(request.estimatedMinutes());
        Task saved = tasks.save(task);

        if (key != null) {
            idempotency.remember(userId, key, RESOURCE_TYPE, saved.getId());
        }
        return new CreateResult<>(TaskResponse.from(saved), true);
    }

    @Transactional
    public TaskResponse update(UUID userId, UUID id, TaskUpdateRequest request) {
        Task task = find(userId, id);

        if (request.projectId() != null && !request.projectId().equals(task.getProjectId())) {
            requireOwnedProject(userId, request.projectId());
            task.setProjectId(request.projectId());
        }
        if (request.title() != null) {
            task.setTitle(request.title());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.status() != null) {
            task.changeStatus(request.status());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (Boolean.TRUE.equals(request.clearDueAt())) {
            task.setDueAt(null);
        } else if (request.dueAt() != null) {
            task.setDueAt(request.dueAt());
        }
        if (request.estimatedMinutes() != null) {
            task.setEstimatedMinutes(request.estimatedMinutes());
        }
        if (request.actualMinutes() != null) {
            task.setActualMinutes(request.actualMinutes());
        }
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        tasks.delete(find(userId, id));
    }

    private Task find(UUID userId, UUID id) {
        return tasks.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc"));
    }

    private void requireOwnedProject(UUID userId, UUID projectId) {
        if (!projects.existsByIdAndUserId(projectId, userId)) {
            throw new ResourceNotFoundException("Không tìm thấy dự án");
        }
    }
}