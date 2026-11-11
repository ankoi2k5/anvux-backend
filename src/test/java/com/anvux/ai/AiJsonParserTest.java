package com.anvux.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.anvux.common.ApiException;

class AiJsonParserTest {

    private static final String VALID = """
            {"projectName": "Ôn phỏng vấn", "summary": "Kế hoạch 2 bước", "tasks": [
              {"title": "Ôn Java", "description": "Đọc lại OOP", "priority": "HIGH",
               "dueAt": "2026-11-12T09:00:00+07:00", "estimatedMinutes": 90, "reminderMinutesBefore": 30},
              {"title": "Luyện câu hỏi", "priority": "LOW"}
            ]}
            """;

    @Test
    void plainJson_isParsed() {
        AiPlanDraft draft = AiJsonParser.parse(VALID);

        assertEquals("Ôn phỏng vấn", draft.projectName());
        assertEquals(2, draft.tasks().size());
        assertEquals("Ôn Java", draft.tasks().get(0).title());
        assertEquals(90, draft.tasks().get(0).estimatedMinutes());
        assertNull(draft.tasks().get(1).dueAt());
    }

    @Test
    void markdownFence_isStripped() {
        AiPlanDraft draft = AiJsonParser.parse("```json\n" + VALID + "\n```");

        assertEquals(2, draft.tasks().size());
    }

    @Test
    void proseAroundJson_isIgnored() {
        AiPlanDraft draft = AiJsonParser.parse("Đây là kế hoạch của bạn:\n" + VALID + "\nChúc bạn thành công!");

        assertEquals(2, draft.tasks().size());
    }

    @Test
    void braceInsideString_doesNotBreakExtraction() {
        AiPlanDraft draft = AiJsonParser.parse(
                "{\"summary\": \"dùng } và { trong chữ\", \"tasks\": [{\"title\": \"Việc {1}\", \"priority\": \"LOW\"}]}");

        assertEquals("Việc {1}", draft.tasks().get(0).title());
    }

    @Test
    void numbersAsStringsOrFloats_areCoerced() {
        AiPlanDraft draft = AiJsonParser.parse(
                "{\"summary\": \"s\", \"tasks\": [{\"title\": \"A\", \"estimatedMinutes\": \"45\", \"reminderMinutesBefore\": 30.0}]}");

        assertEquals(45, draft.tasks().get(0).estimatedMinutes());
        assertEquals(30, draft.tasks().get(0).reminderMinutesBefore());
    }

    @Test
    void nonNumericMinutes_becomeNullInsteadOfFailing() {
        AiPlanDraft draft = AiJsonParser.parse(
                "{\"summary\": \"s\", \"tasks\": [{\"title\": \"A\", \"estimatedMinutes\": \"khoảng một giờ\"}]}");

        assertNull(draft.tasks().get(0).estimatedMinutes());
    }

    @Test
    void nonObjectTaskEntries_areSkipped() {
        AiPlanDraft draft = AiJsonParser.parse(
                "{\"summary\": \"s\", \"tasks\": [\"chuỗi lạ\", 42, {\"title\": \"Việc thật\"}]}");

        assertEquals(1, draft.tasks().size());
    }

    @Test
    void emptyTasksArray_isParsedAsEmpty() {
        assertEquals(0, AiJsonParser.parse("{\"summary\": \"\", \"tasks\": []}").tasks().size());
    }

    @Test
    void garbage_isRejectedAsInvalidOutput() {
        for (String text : new String[] {null, "", "   ", "xin chào", "[1, 2, 3]", "{\"summary\": \"thiếu tasks\"}",
                "{\"tasks\": \"không phải mảng\"}", "{ chưa đóng"}) {
            ApiException ex = assertThrows(ApiException.class, () -> AiJsonParser.parse(text));
            assertEquals("AI_INVALID_OUTPUT", ex.getCode());
        }
    }
}
