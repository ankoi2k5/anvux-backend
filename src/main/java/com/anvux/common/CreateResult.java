package com.anvux.common;

/** Kết quả tạo mới: created = false nghĩa là yêu cầu trùng khóa và được trả lại bản ghi cũ. */
public record CreateResult<T>(T body, boolean created) {
}