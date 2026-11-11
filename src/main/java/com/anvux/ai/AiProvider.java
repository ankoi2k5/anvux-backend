package com.anvux.ai;

public interface AiProvider {

    /** Tên ngắn lưu vào cơ sở dữ liệu, ví dụ "fake" hoặc "anthropic". */
    String name();

    AiPlanResult generatePlan(AiPlanRequest request);
}