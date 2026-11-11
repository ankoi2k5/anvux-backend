package com.anvux.ai.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiPlanCreateRequest(
        @NotBlank(message = "Vui lòng nhập mục tiêu bạn muốn lập kế hoạch")
        @Size(max = 1000, message = "Mục tiêu tối đa 1000 ký tự")
        String prompt,

        /** Nếu có, các công việc sẽ được thêm vào dự án này thay vì tạo dự án mới. */
        UUID targetProjectId,

        /** Múi giờ IANA của người dùng, ví dụ Asia/Ho_Chi_Minh. Bỏ trống thì dùng Asia/Ho_Chi_Minh. */
        @Size(max = 50, message = "Múi giờ không hợp lệ")
        String timeZone) {
}