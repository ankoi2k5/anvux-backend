package com.anvux.ai;

import org.springframework.http.HttpStatus;

import com.anvux.common.ApiException;

public class AiProviderException extends ApiException {

    public AiProviderException(String code, String message) {
        super(HttpStatus.BAD_GATEWAY, code, message);
    }

    public static AiProviderException unavailable() {
        return new AiProviderException("AI_UNAVAILABLE",
                "Trợ lý AI tạm thời không phản hồi, vui lòng thử lại sau");
    }

    /** Nhà cung cấp AI (thường là gói miễn phí) báo hết lượt trong phút/ngày. */
    public static AiProviderException busy() {
        return new AiProviderException("AI_PROVIDER_BUSY",
                "Trợ lý AI đang quá tải hoặc hết lượt miễn phí, vui lòng thử lại sau ít phút");
    }

    public static AiProviderException invalidOutput() {
        return new AiProviderException("AI_INVALID_OUTPUT",
                "Trợ lý AI trả về kết quả không hợp lệ, vui lòng thử lại");
    }
}