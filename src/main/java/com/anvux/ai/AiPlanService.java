package com.anvux.ai;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.anvux.ai.dto.AiPlanApproveRequest;
import com.anvux.ai.dto.AiPlanCreateRequest;
import com.anvux.ai.dto.AiPlanItemInput;
import com.anvux.ai.dto.AiPlanResponse;
import com.anvux.common.ApiException;
import com.anvux.common.ResourceNotFoundException;
import com.anvux.project.Project;
import com.anvux.project.ProjectRepository;
import com.anvux.reminder.Reminder;
import com.anvux.reminder.ReminderRepository;
import com.anvux.task.Task;
import com.anvux.task.TaskPriority;
import com.anvux.task.TaskRepository;

@Service
public class AiPlanService {

    private static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";

    private final AiProvider provider;
    private final AiProperties props;
    private final AiPlanValidator validator;
    private final AiPlanRepository plans;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final ReminderRepository reminders;
    private final TransactionTemplate tx;

    public AiPlanService(AiProvider provider,
                         AiProperties props,
                         AiPlanValidator validator,
                         AiPlanRepository plans,
                         ProjectRepository projects,
                         TaskRepository tasks,
                         ReminderRepository reminders,
                         PlatformTransactionManager transactionManager) {
        this.provider = provider;
        this.props = props;
        this.validator = validator;
        this.plans = plans;
        this.projects = projects;
        this.tasks = tasks;
        this.reminders = reminders;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Tạo bản nháp. Không đánh dấu @Transactional ở cấp hàm: lệnh gọi AI có thể mất hàng chục giây,
     * ta không muốn giữ kết nối cơ sở dữ liệu trong lúc chờ. Chỉ phần lưu mới nằm trong giao dịch.
     */
    public AiPlanResponse create(UUID userId, AiPlanCreateRequest request) {
        ZoneId zone = parseZone(request.timeZone());
        String prompt = request.prompt().trim();

        Project target = null;
        if (request.targetProjectId() != null) {
            target = projects.findByIdAndUserId(request.targetProjectId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án"));
        }

        Instant now = Instant.now();
        enforceRateLimit(userId, now);

        AiPlanResult result = provider.generatePlan(
                new AiPlanRequest(prompt, target == null ? null : target.getName(), zone, now));
        AiPlanValidator.ValidatedPlan validated =
                validator.validate(result.draft(), "Kế hoạch: " + abbreviate(prompt, 40), now, zone);

        UUID targetId = target == null ? null : target.getId();
        String projectName = target == null ? validated.projectName() : null;

        AiPlanResponse response = tx.execute(status -> {
            AiPlan plan = new AiPlan(userId, prompt, targetId, projectName, validated.summary(),
                    provider.name(), result.model());
            int order = 0;
            for (AiPlanValidator.ValidatedItem item : validated.items()) {
                plan.addItem(new AiPlanItem(order++, item.title(), item.description(), item.priority(),
                        item.dueAt(), item.estimatedMinutes(), item.reminderMinutesBefore()));
            }
            return AiPlanResponse.from(plans.save(plan));
        });
        return Objects.requireNonNull(response);
    }

    @Transactional(readOnly = true)
    public List<AiPlanResponse> list(UUID userId) {
        return plans.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AiPlanResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiPlanResponse get(UUID userId, UUID id) {
        return AiPlanResponse.from(find(userId, id));
    }

    /**
     * Duyệt kế hoạch: tạo dự án (nếu cần), các công việc và nhắc nhở trong MỘT giao dịch.
     * Bất kỳ lỗi nào cũng hoàn tác toàn bộ. Duyệt lại một kế hoạch đã thực hiện thì trả lại kết quả cũ.
     */
    @Transactional
    public AiPlanResponse approve(UUID userId, UUID id, AiPlanApproveRequest request) {
        AiPlan plan = find(userId, id);

        if (plan.getStatus() == AiPlanStatus.EXECUTED) {
            return AiPlanResponse.from(plan);
        }
        if (plan.getStatus() != AiPlanStatus.DRAFT) {
            throw notDraft();
        }

        // Áp dụng chỉnh sửa của người dùng (nếu có); chúng đã qua kiểm tra bằng Bean Validation.
        if (request != null && request.items() != null) {
            List<AiPlanItem> edited = toItems(request.items());
            if (edited.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_EMPTY",
                        "Kế hoạch cần ít nhất một công việc");
            }
            plan.replaceItems(edited);
        }
        if (request != null && request.projectName() != null && !request.projectName().isBlank()) {
            plan.setProjectName(request.projectName().trim());
        }

        UUID projectId = resolveProject(userId, plan);

        for (AiPlanItem item : plan.getItems()) {
            Task task = new Task(userId, projectId, item.getTitle());
            task.setDescription(item.getDescription());
            task.setPriority(item.getPriority());
            task.setDueAt(item.getDueAt());
            task.setEstimatedMinutes(item.getEstimatedMinutes());
            Task saved = tasks.save(task);
            item.setCreatedTaskId(saved.getId());

            if (item.getDueAt() != null && item.getReminderMinutesBefore() != null) {
                reminders.save(new Reminder(userId, saved.getId(),
                        item.getDueAt().minus(item.getReminderMinutesBefore(), ChronoUnit.MINUTES)));
            }
        }

        plan.markExecuted();
        return AiPlanResponse.from(plan);
    }

    @Transactional
    public AiPlanResponse reject(UUID userId, UUID id) {
        AiPlan plan = find(userId, id);
        if (plan.getStatus() == AiPlanStatus.REJECTED) {
            return AiPlanResponse.from(plan);
        }
        if (plan.getStatus() != AiPlanStatus.DRAFT) {
            throw notDraft();
        }
        plan.reject();
        return AiPlanResponse.from(plan);
    }

    // ---------- Hàm hỗ trợ ----------

    private AiPlan find(UUID userId, UUID id) {
        return plans.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kế hoạch"));
    }

    private UUID resolveProject(UUID userId, AiPlan plan) {
        if (plan.getTargetProjectId() != null) {
            return projects.findByIdAndUserId(plan.getTargetProjectId(), userId)
                    .map(Project::getId)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "TARGET_PROJECT_GONE",
                            "Dự án đích không còn tồn tại"));
        }
        String name = plan.getProjectName() == null || plan.getProjectName().isBlank()
                ? "Kế hoạch AI"
                : plan.getProjectName();
        Project project = projects.save(new Project(userId, name, plan.getSummary(), null));
        plan.setCreatedProjectId(project.getId());
        return project.getId();
    }

    private List<AiPlanItem> toItems(List<AiPlanItemInput> inputs) {
        List<AiPlanItem> result = new ArrayList<>();
        int order = 0;
        for (AiPlanItemInput input : inputs) {
            if (input == null) {
                continue;
            }
            // Nhắc nhở tính theo hạn chót nên không có hạn thì bỏ nhắc nhở.
            Integer reminder = input.dueAt() == null ? null : input.reminderMinutesBefore();
            result.add(new AiPlanItem(order++, input.title(), input.description(),
                    input.priority() == null ? TaskPriority.MEDIUM : input.priority(),
                    input.dueAt(), input.estimatedMinutes(), reminder));
        }
        return result;
    }

    private void enforceRateLimit(UUID userId, Instant now) {
        boolean overHour = plans.countByUserIdAndCreatedAtAfter(userId, now.minus(1, ChronoUnit.HOURS))
                >= props.maxPlansPerHour();
        boolean overDay = plans.countByUserIdAndCreatedAtAfter(userId, now.minus(1, ChronoUnit.DAYS))
                >= props.maxPlansPerDay();
        if (overHour || overDay) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Bạn đã dùng trợ lý AI quá nhiều lần, vui lòng thử lại sau");
        }
    }

    private static ZoneId parseZone(String value) {
        String id = value == null || value.isBlank() ? DEFAULT_ZONE : value.trim();
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Múi giờ không hợp lệ");
        }
    }

    private static ApiException notDraft() {
        return new ApiException(HttpStatus.CONFLICT, "PLAN_NOT_DRAFT", "Kế hoạch này đã được xử lý");
    }

    private static String abbreviate(String text, int max) {
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max).trim();
    }
}