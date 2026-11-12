package com.anvux.reminder;

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
import com.anvux.reminder.dto.ReminderCreateRequest;
import com.anvux.reminder.dto.ReminderResponse;
import com.anvux.reminder.dto.ReminderUpdateRequest;
import com.anvux.task.TaskRepository;

@Service
@Transactional(readOnly = true)
public class ReminderService {

    private static final int MAX_PER_TASK = 10;
    private static final int MAX_RESULTS = 500;
    private static final String RESOURCE_TYPE = "REMINDER";

    private final ReminderRepository reminders;
    private final TaskRepository tasks;
    private final IdempotencyService idempotency;

    public ReminderService(ReminderRepository reminders, TaskRepository tasks, IdempotencyService idempotency) {
        this.reminders = reminders;
        this.tasks = tasks;
        this.idempotency = idempotency;
    }

    public List<ReminderResponse> list(UUID userId, UUID taskId, Boolean enabled, Instant from, Instant to) {
        return reminders.findAll(
                        ReminderSpecifications.filter(userId, taskId, enabled, from, to),
                        PageRequest.of(0, MAX_RESULTS, Sort.by("remindAt").ascending()))
                .getContent().stream()
                .map(ReminderResponse::from)
                .toList();
    }

    @Transactional
    public CreateResult<ReminderResponse> create(UUID userId, String idempotencyKey, ReminderCreateRequest request) {
        String key = idempotency.validate(idempotencyKey);

        if (key != null) {
            Optional<Reminder> existing = idempotency.findResourceId(userId, key, RESOURCE_TYPE)
                    .flatMap(id -> reminders.findByIdAndUserId(id, userId));
            if (existing.isPresent()) {
                return new CreateResult<>(ReminderResponse.from(existing.get()), false);
            }
        }
        if (request.id() != null) {
            Optional<Reminder> mine = reminders.findByIdAndUserId(request.id(), userId);
            if (mine.isPresent()) {
                return new CreateResult<>(ReminderResponse.from(mine.get()), false);
            }
            if (reminders.existsById(request.id())) {
                throw new ApiException(HttpStatus.CONFLICT, "ID_ALREADY_USED", "Mã này đã được dùng");
            }
        }
        // Công việc của người khác coi như không tồn tại.
        if (tasks.findByIdAndUserId(request.taskId(), userId).isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy công việc");
        }
        if (reminders.countByTaskId(request.taskId()) >= MAX_PER_TASK) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_REACHED",
                    "Mỗi công việc tối đa " + MAX_PER_TASK + " nhắc nhở");
        }
        if (reminders.existsByTaskIdAndRemindAt(request.taskId(), request.remindAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "REMINDER_EXISTS", "Nhắc nhở này đã tồn tại");
        }

        Reminder saved = reminders.save(
                new Reminder(request.id(), userId, request.taskId(), request.remindAt()));
        if (key != null) {
            idempotency.remember(userId, key, RESOURCE_TYPE, saved.getId());
        }
        return new CreateResult<>(ReminderResponse.from(saved), true);
    }

    @Transactional
    public ReminderResponse update(UUID userId, UUID id, ReminderUpdateRequest request) {
        Reminder reminder = find(userId, id);

        if (request.remindAt() != null && !request.remindAt().equals(reminder.getRemindAt())) {
            if (reminders.existsByTaskIdAndRemindAt(reminder.getTaskId(), request.remindAt())) {
                throw new ApiException(HttpStatus.CONFLICT, "REMINDER_EXISTS", "Nhắc nhở này đã tồn tại");
            }
            reminder.setRemindAt(request.remindAt());
        }
        if (request.enabled() != null) {
            reminder.setEnabled(request.enabled());
        }
        return ReminderResponse.from(reminder);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        reminders.delete(find(userId, id));
    }

    private Reminder find(UUID userId, UUID id) {
        return reminders.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhắc nhở"));
    }
}