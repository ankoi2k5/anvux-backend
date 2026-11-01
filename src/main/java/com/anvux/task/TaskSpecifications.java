package com.anvux.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

final class TaskSpecifications {

    private TaskSpecifications() {
    }

    /** Luôn lọc theo chủ sở hữu, các điều kiện còn lại chỉ áp dụng khi được truyền vào. */
    static Specification<Task> filter(UUID userId,
                                      UUID projectId,
                                      TaskStatus status,
                                      TaskPriority priority,
                                      Instant dueAfter,
                                      Instant dueBefore) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (dueAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("dueAt"), dueAfter));
            }
            if (dueBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("dueAt"), dueBefore));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}