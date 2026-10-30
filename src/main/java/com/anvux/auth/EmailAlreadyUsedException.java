package com.anvux.auth;

import org.springframework.http.HttpStatus;

import com.anvux.common.ApiException;

public class EmailAlreadyUsedException extends ApiException {

    public EmailAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "Email đã được sử dụng");
    }
}