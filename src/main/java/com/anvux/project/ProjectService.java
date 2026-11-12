package com.anvux.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.anvux.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvux.common.CreateResult;
import com.anvux.common.IdempotencyService;
import com.anvux.common.ResourceNotFoundException;
import com.anvux.project.dto.ProjectCreateRequest;
import com.anvux.project.dto.ProjectResponse;
import com.anvux.project.dto.ProjectUpdateRequest;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private static final String RESOURCE_TYPE = "PROJECT";

    private final ProjectRepository projects;
    private final IdempotencyService idempotency;

    public ProjectService(ProjectRepository projects, IdempotencyService idempotency) {
        this.projects = projects;
        this.idempotency = idempotency;
    }

    public List<ProjectResponse> list(UUID userId, boolean includeArchived) {
        List<Project> result = includeArchived
                ? projects.findByUserIdOrderByCreatedAtDesc(userId)
                : projects.findByUserIdAndArchivedFalseOrderByCreatedAtDesc(userId);
        return result.stream().map(ProjectResponse::from).toList();
    }

    public ProjectResponse get(UUID userId, UUID id) {
        return ProjectResponse.from(find(userId, id));
    }

    @Transactional
    public CreateResult<ProjectResponse> create(UUID userId, String idempotencyKey, ProjectCreateRequest request) {
        String key = idempotency.validate(idempotencyKey);

        if (key != null) {
            Optional<Project> existing = idempotency.findResourceId(userId, key, RESOURCE_TYPE)
                    .flatMap(id -> projects.findByIdAndUserId(id, userId));
            if (existing.isPresent()) {
                return new CreateResult<>(ProjectResponse.from(existing.get()), false);
            }
        }
        if (request.id() != null) {
            Optional<Project> mine = projects.findByIdAndUserId(request.id(), userId);
            if (mine.isPresent()) {
                return new CreateResult<>(ProjectResponse.from(mine.get()), false);
            }
            if (projects.existsById(request.id())) {
                throw new ApiException(HttpStatus.CONFLICT, "ID_ALREADY_USED", "Mã này đã được dùng");
            }
        }

        Project project = projects.save(
                new Project(request.id(), userId, request.name(), request.description(), request.color()));
        if (key != null) {
            idempotency.remember(userId, key, RESOURCE_TYPE, project.getId());
        }
        return new CreateResult<>(ProjectResponse.from(project), true);
    }

    @Transactional
    public ProjectResponse update(UUID userId, UUID id, ProjectUpdateRequest request) {
        Project project = find(userId, id);
        if (request.name() != null) {
            project.rename(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.color() != null) {
            project.setColor(request.color());
        }
        if (request.archived() != null) {
            project.setArchived(request.archived());
        }
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        projects.delete(find(userId, id));
    }

    /** Tìm theo cả id và chủ sở hữu: dự án của người khác coi như không tồn tại. */
    private Project find(UUID userId, UUID id) {
        return projects.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án"));
    }
}