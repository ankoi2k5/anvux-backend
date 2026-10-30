package com.anvux.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập email") String email,
        @NotBlank(message = "Vui lòng nhập mật khẩu") String password) {
}