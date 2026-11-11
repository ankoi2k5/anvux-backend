package com.anvux.ai;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anvux.ai.AiPlanDraft.AiTaskDraft;

/** Tạo kế hoạch mẫu ngay trong máy: dùng cho test và khi chưa có khóa API. */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeAiProvider implements AiProvider {

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public AiPlanResult generatePlan(AiPlanRequest request) {
        String goal = shorten(request.goal(), 60);
        ZonedDateTime base = request.now().atZone(request.zone());

        List<AiTaskDraft> tasks = List.of(
                new AiTaskDraft("Tìm hiểu yêu cầu: " + goal,
                        "Đọc kỹ yêu cầu và liệt kê những gì cần chuẩn bị.",
                        "HIGH", due(base, 1), 30, 30),
                new AiTaskDraft("Lập dàn ý và chia việc nhỏ",
                        "Chia mục tiêu thành các bước làm được trong một buổi.",
                        "MEDIUM", due(base, 2), 60, null),
                new AiTaskDraft("Thực hiện phần chính",
                        "Tập trung làm phần quan trọng nhất, tắt thông báo khi làm.",
                        "MEDIUM", due(base, 4), 120, 60),
                new AiTaskDraft("Rà soát và hoàn thiện",
                        "Đọc lại, sửa lỗi và chuẩn bị nộp hoặc trình bày.",
                        "LOW", due(base, 5), 45, null));

        String projectName = request.targetProjectName() == null
                ? "Kế hoạch: " + shorten(request.goal(), 40)
                : null;

        return new AiPlanResult(
                new AiPlanDraft(projectName, "Kế hoạch mẫu gồm 4 bước cho mục tiêu: " + goal, tasks),
                "fake-planner");
    }

    private static String due(ZonedDateTime base, int daysFromNow) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                base.plusDays(daysFromNow).withHour(9).withMinute(0).withSecond(0).withNano(0));
    }

    private static String shorten(String text, int max) {
        String oneLine = text.trim().replaceAll("\\s+", " ");
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max).trim();
    }
}