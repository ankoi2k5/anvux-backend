package com.anvux.ai;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiPlanRepository extends JpaRepository<AiPlan, UUID> {

    Optional<AiPlan> findByIdAndUserId(UUID id, UUID userId);

    List<AiPlan> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}