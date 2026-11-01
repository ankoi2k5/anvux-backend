package com.anvux.project;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvux.common.ResourceNotFoundException;
import com.anvux.project.dto.ProjectCreateRequest;
import com.anvux.project.dto.ProjectResponse;
import com.anvux.project.dto.ProjectUpdateRequest;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
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
    public ProjectResponse create(UUID userId, ProjectCreateRequest request) {
        Project project = projects.save(
                new Project(userId, request.name(), request.description(), request.color()));
        return ProjectResponse.from(project);
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