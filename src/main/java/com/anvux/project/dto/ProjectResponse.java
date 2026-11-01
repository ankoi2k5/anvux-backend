package com.anvux.project.dto;

import java.time.Instant;
import java.util.UUID;

import com.anvux.project.Project;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String color,
        boolean archived,
        Long version,
        Instant createdAt,
        Instant updatedAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getColor(),
                project.isArchived(),
                project.getVersion(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}