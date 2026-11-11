package com.anvux.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anvux.ai.dto.AiPlanApproveRequest;
import com.anvux.ai.dto.AiPlanCreateRequest;
import com.anvux.ai.dto.AiPlanResponse;
import com.anvux.auth.CurrentUser;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai/plans")
@SecurityRequirement(name = "bearerAuth")
public class AiPlanController {

    private final AiPlanService aiPlanService;

    public AiPlanController(AiPlanService aiPlanService) {
        this.aiPlanService = aiPlanService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AiPlanResponse create(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody AiPlanCreateRequest request) {
        return aiPlanService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping
    public List<AiPlanResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return aiPlanService.list(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public AiPlanResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return aiPlanService.get(CurrentUser.id(jwt), id);
    }

    /** Thân yêu cầu không bắt buộc: nếu có, chứa các chỉnh sửa của người dùng trước khi duyệt. */
    @PostMapping("/{id}/approve")
    public AiPlanResponse approve(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody(required = false) AiPlanApproveRequest request) {
        return aiPlanService.approve(CurrentUser.id(jwt), id, request);
    }

    @PostMapping("/{id}/reject")
    public AiPlanResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return aiPlanService.reject(CurrentUser.id(jwt), id);
    }
}