package com.anvux.auth;

import org.springframework.http.HttpStatus;

import com.anvux.common.ApiException;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
    }
}