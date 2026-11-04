package com.anvux.reminder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

final class ReminderSpecifications {

    private ReminderSpecifications() {
    }

    /** Luôn lọc theo chủ sở hữu, các điều kiện còn lại chỉ áp dụng khi được truyền vào. */
    static Specification<Reminder> filter(UUID userId, UUID taskId, Boolean enabled, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (taskId != null) {
                predicates.add(cb.equal(root.get("taskId"), taskId));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("remindAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("remindAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}