package com.anvux.auth;

import org.springframework.http.HttpStatus;

import com.anvux.common.ApiException;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
    }
}