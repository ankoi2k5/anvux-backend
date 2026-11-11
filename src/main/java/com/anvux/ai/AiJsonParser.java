package com.anvux.ai;

import java.util.ArrayList;
import java.util.List;

import com.anvux.ai.AiPlanDraft.AiTaskDraft;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Đọc văn bản mà mô hình AI trả về và chuyển thành {@link AiPlanDraft}.
 *
 * <p>Mô hình miễn phí thường không tuân thủ lược đồ tuyệt đối: bọc JSON trong khối ```json,
 * viết thêm câu dẫn trước/sau, trả số dưới dạng chuỗi ("60") hoặc thiếu trường. Lớp này chịu được
 * các lỗi đó. Nếu không tìm được JSON object nào thì ném {@link AiProviderException#invalidOutput()}.
 * Việc kiểm tra nghiệp vụ (hạn chót, độ dài, ưu tiên...) vẫn do {@link AiPlanValidator} làm.
 */
final class AiJsonParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AiJsonParser() {
    }

    static AiPlanDraft parse(String text) {
        JsonNode root = readObject(text);
        JsonNode tasksNode = root.path("tasks");
        if (!tasksNode.isArray()) {
            throw AiProviderException.invalidOutput();
        }

        List<AiTaskDraft> tasks = new ArrayList<>();
        for (JsonNode node : tasksNode) {
            if (node.isObject()) {
                tasks.add(new AiTaskDraft(
                        text(node, "title"),
                        text(node, "description"),
                        text(node, "priority"),
                        text(node, "dueAt"),
                        integer(node, "estimatedMinutes"),
                        integer(node, "reminderMinutesBefore")));
            }
        }
        return new AiPlanDraft(text(root, "projectName"), text(root, "summary"), tasks);
    }

    /** Tìm JSON object đầu tiên trong văn bản, bỏ qua hàng rào ``` và lời dẫn. */
    private static JsonNode readObject(String text) {
        if (text == null || text.isBlank()) {
            throw AiProviderException.invalidOutput();
        }
        String candidate = stripFences(text.trim());
        int start = candidate.indexOf('{');
        while (start >= 0) {
            int end = matchingBrace(candidate, start);
            if (end > start) {
                try {
                    JsonNode node = MAPPER.readTree(candidate.substring(start, end + 1));
                    if (node != null && node.isObject()) {
                        return node;
                    }
                } catch (RuntimeException ignored) {
                    // thử object kế tiếp
                }
            }
            start = candidate.indexOf('{', start + 1);
        }
        throw AiProviderException.invalidOutput();
    }

    private static String stripFences(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        String body = firstNewline < 0 ? text : text.substring(firstNewline + 1);
        int lastFence = body.lastIndexOf("```");
        return (lastFence >= 0 ? body.substring(0, lastFence) : body).trim();
    }

    /** Tìm dấu } đóng khớp với { ở vị trí start, có tính đến chuỗi và ký tự thoát. */
    private static int matchingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isString()) {
            return value.stringValue();
        }
        return value.isNumber() ? value.asString() : null;
    }

    /** Chấp nhận 60, 60.0 và "60"; giá trị không phải số trả về null thay vì làm hỏng cả kế hoạch. */
    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        try {
            if (value.isNumber()) {
                return value.intValue();
            }
            if (value.isString()) {
                return (int) Math.round(Double.parseDouble(value.stringValue().trim()));
            }
        } catch (NumberFormatException ignored) {
            // rơi xuống null
        }
        return null;
    }
}
