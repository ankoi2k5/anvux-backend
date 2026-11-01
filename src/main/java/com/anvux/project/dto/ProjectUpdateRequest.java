package com.anvux.project.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "Tên dự án không được để trống")
        @Size(max = 120, message = "Tên dự án tối đa 120 ký tự")
        String name,

        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Màu phải có dạng #RRGGBB")
        String color,

        Boolean archived) {
}