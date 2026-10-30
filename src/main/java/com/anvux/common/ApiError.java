package com.anvux.common;

import java.time.Instant;
import java.util.List;

public record ApiError(String code, String message, List<Detail> details, Instant timestamp) {

    public record Detail(String field, String message) {
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of(), Instant.now());
    }
}