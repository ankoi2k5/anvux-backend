package com.anvux.auth;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    /** Lấy id người dùng từ token đăng nhập (trường subject). */
    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}