package com.anvux.task;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvux.common.ApiException;
import com.anvux.common.CreateResult;
import com.anvux.common.IdempotencyService;
import com.anvux.common.ResourceNotFoundException;
import com.anvux.project.ProjectRepository;
import com.anvux.reminder.Reminder;
import com.anvux.reminder.ReminderRepository;
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
    private final ReminderRepository reminders;
    private final IdempotencyService idempotency;

    public TaskService(TaskRepository tasks,
                       ProjectRepository projects,
                       ReminderRepository reminders,
                       IdempotencyService idempotency) {
        this.tasks = tasks;
        this.projects = projects;
        this.reminders = reminders;
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
        if (request.id() != null) {
            Optional<Task> mine = tasks.findByIdAndUserId(request.id(), userId);
            if (mine.isPresent()) {
                return new CreateResult<>(TaskResponse.from(mine.get()), false);
            }
            if (tasks.existsById(request.id())) {
                throw new ApiException(HttpStatus.CONFLICT, "ID_ALREADY_USED", "Mã này đã được dùng");
            }
        }

        requireOwnedProject(userId, request.projectId());

        TaskRecurrence recurrence = request.recurrence() == null ? TaskRecurrence.NONE : request.recurrence();
        requireDueDateForRecurrence(recurrence, request.dueAt());

        Task task = new Task(request.id(), userId, request.projectId(), request.title());
        task.setDescription(request.description());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setDueAt(request.dueAt());
        task.setEstimatedMinutes(request.estimatedMinutes());
        task.setRecurrence(recurrence);
        Task saved = tasks.save(task);

        if (key != null) {
            idempotency.remember(userId, key, RESOURCE_TYPE, saved.getId());
        }
        return new CreateResult<>(TaskResponse.from(saved), true);
    }

    @Transactional
    public TaskResponse update(UUID userId, UUID id, TaskUpdateRequest request) {
        Task task = find(userId, id);
        boolean wasDone = task.getStatus() == TaskStatus.DONE;

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
        if (request.recurrence() != null) {
            task.setRecurrence(request.recurrence());
        }

        requireDueDateForRecurrence(task.getRecurrence(), task.getDueAt());

        // Vừa chuyển sang Hoàn thành: nếu là việc lặp lại thì sinh việc kế tiếp.
        if (!wasDone && task.getStatus() == TaskStatus.DONE) {
            spawnNextOccurrence(task);
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

    private void requireDueDateForRecurrence(TaskRecurrence recurrence, Instant dueAt) {
        if (recurrence != TaskRecurrence.NONE && dueAt == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RECURRENCE_NEEDS_DUE_DATE",
                    "Công việc lặp lại cần có hạn chót");
        }
    }

    /**
     * Tạo bản kế tiếp của một việc lặp lại vừa hoàn thành, kèm các nhắc nhở dịch theo hạn chót mới.
     * Nếu hoàn thành trễ, hạn mới được đẩy tới lần lặp gần nhất còn ở tương lai (giữ nguyên giờ trong ngày).
     */
    private void spawnNextOccurrence(Task done) {
        Duration period = done.getRecurrence().period();
        if (period == null || done.getSpawnedNextId() != null || done.getDueAt() == null) {
            return;
        }

        Instant now = Instant.now();
        Instant oldDue = done.getDueAt();
        Instant nextDue = oldDue.plus(period);
        if (!nextDue.isAfter(now)) {
            long skipped = Duration.between(nextDue, now).toSeconds() / period.toSeconds() + 1;
            nextDue = nextDue.plus(period.multipliedBy(skipped));
        }

        Task next = new Task(done.getUserId(), done.getProjectId(), done.getTitle());
        next.setDescription(done.getDescription());
        next.setPriority(done.getPriority());
        next.setDueAt(nextDue);
        next.setEstimatedMinutes(done.getEstimatedMinutes());
        next.setRecurrence(done.getRecurrence());
        Task saved = tasks.save(next);
        done.setSpawnedNextId(saved.getId());

        Duration shift = Duration.between(oldDue, nextDue);
        for (Reminder reminder : reminders.findByTaskId(done.getId())) {
            Reminder copy = new Reminder(done.getUserId(), saved.getId(), reminder.getRemindAt().plus(shift));
            copy.setEnabled(reminder.isEnabled());
            reminders.save(copy);
        }
    }
}