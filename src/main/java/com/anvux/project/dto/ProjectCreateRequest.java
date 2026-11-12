package com.anvux.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProjectCreateRequest(
        UUID id,
        @NotBlank(message = "Vui lòng nhập tên dự án")
        @Size(max = 120, message = "Tên dự án tối đa 120 ký tự")
        String name,

        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Màu phải có dạng #RRGGBB")
        String color) {
}