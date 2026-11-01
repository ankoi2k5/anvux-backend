package com.anvux.project;

import java.util.List;
import java.util.UUID;

import com.anvux.project.dto.ProjectCreateRequest;
import com.anvux.project.dto.ProjectResponse;
import com.anvux.project.dto.ProjectUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import com.anvux.common.CreateResult;
import com.anvux.auth.CurrentUser;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/projects")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "false") boolean includeArchived) {
        return projectService.list(CurrentUser.id(jwt), includeArchived);
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProjectCreateRequest request) {
        CreateResult<ProjectResponse> result =
                projectService.create(CurrentUser.id(jwt), idempotencyKey, request);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.body());
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return projectService.get(CurrentUser.id(jwt), id);
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody ProjectUpdateRequest request) {
        return projectService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        projectService.delete(CurrentUser.id(jwt), id);
    }
}