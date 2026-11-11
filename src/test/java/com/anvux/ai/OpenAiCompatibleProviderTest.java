package com.anvux.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anvux.common.ApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Giả lập máy chủ AI bằng HttpServer có sẵn trong JDK: không cần khóa API, không cần mạng. */
class OpenAiCompatibleProviderTest {

    record Call(String path, String authorization, String body) {
    }

    private static final String PLAN_JSON = "{\"projectName\":\"Dự án\",\"summary\":\"Tóm tắt\","
            + "\"tasks\":[{\"title\":\"Việc 1\",\"priority\":\"HIGH\"}]}";

    private HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile Function<Call, Reply> handler;

    record Reply(int status, String body) {
        static Reply ok(String content) {
            String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            return new Reply(200, "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"content\":\""
                    + escaped + "\"}}]}");
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Call call = new Call(exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"), body);
        calls.add(call);
        Reply reply = handler.apply(call);
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private OpenAiCompatibleProvider provider(int maxRetries) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/openai";
        return new OpenAiCompatibleProvider(
                new AiProperties("openai", "secret-key", "test-model", baseUrl, 5, maxRetries, 10, 30));
    }

    private static AiPlanRequest request() {
        return new AiPlanRequest("Chuẩn bị phỏng vấn", null, ZoneId.of("Asia/Ho_Chi_Minh"), Instant.now());
    }

    @Test
    void validResponse_returnsDraftAndSendsExpectedRequest() {
        handler = call -> Reply.ok(PLAN_JSON);

        AiPlanResult result = provider(0).generatePlan(request());

        assertEquals(1, result.draft().tasks().size());
        assertEquals("test-model", result.model());
        assertEquals(1, calls.size());
        Call call = calls.get(0);
        assertEquals("/v1beta/openai/chat/completions", call.path());
        assertEquals("Bearer secret-key", call.authorization());
        assertTrue(call.body().contains("\"json_object\""));
        assertTrue(call.body().contains("<yeu_cau_nguoi_dung>"));
    }

    @Test
    void fencedJsonWithProse_isAccepted() {
        handler = call -> Reply.ok("Đây là kế hoạch:\n```json\n" + PLAN_JSON + "\n```");

        assertEquals(1, provider(0).generatePlan(request()).draft().tasks().size());
    }

    @Test
    void invalidFirstAnswer_isRepairedWithOneRetry() {
        handler = call -> calls.size() == 1 ? Reply.ok("Xin lỗi, tôi không chắc") : Reply.ok(PLAN_JSON);

        AiPlanResult result = provider(0).generatePlan(request());

        assertEquals(1, result.draft().tasks().size());
        assertEquals(2, calls.size());
        assertTrue(calls.get(1).body().contains("không đọc được"));
    }

    @Test
    void alwaysInvalid_failsAfterExactlyOneRepair() {
        handler = call -> Reply.ok("không phải JSON");

        ApiException ex = assertThrows(ApiException.class, () -> provider(2).generatePlan(request()));

        assertEquals("AI_INVALID_OUTPUT", ex.getCode());
        assertEquals(2, calls.size());
    }

    @Test
    void badRequestOnResponseFormat_retriesWithoutJsonMode() {
        handler = call -> call.body().contains("response_format")
                ? new Reply(400, "{\"error\":\"response_format not supported\"}")
                : Reply.ok(PLAN_JSON);

        AiPlanResult result = provider(0).generatePlan(request());

        assertEquals(1, result.draft().tasks().size());
        assertEquals(2, calls.size());
        assertFalse(calls.get(1).body().contains("response_format"));
    }

    @Test
    void rateLimited_mapsToBusyError() {
        handler = call -> new Reply(429, "{\"error\":\"quota\"}");

        ApiException ex = assertThrows(ApiException.class, () -> provider(0).generatePlan(request()));

        assertEquals("AI_PROVIDER_BUSY", ex.getCode());
    }

    @Test
    void serverError_mapsToUnavailable() {
        handler = call -> new Reply(500, "{}");

        ApiException ex = assertThrows(ApiException.class, () -> provider(0).generatePlan(request()));

        assertEquals("AI_UNAVAILABLE", ex.getCode());
    }

    @Test
    void emptyChoices_isInvalidOutput() {
        handler = call -> new Reply(200, "{\"choices\":[]}");

        ApiException ex = assertThrows(ApiException.class, () -> provider(0).generatePlan(request()));

        assertEquals("AI_INVALID_OUTPUT", ex.getCode());
    }

    @Test
    void providerLabel_isDerivedFromBaseUrl() {
        assertEquals("gemini", OpenAiCompatibleProvider.labelFor(
                "https://generativelanguage.googleapis.com/v1beta/openai"));
        assertEquals("groq", OpenAiCompatibleProvider.labelFor("https://api.groq.com/openai/v1"));
        assertEquals("openrouter", OpenAiCompatibleProvider.labelFor("https://openrouter.ai/api/v1"));
        assertEquals("openai-compat", OpenAiCompatibleProvider.labelFor("http://localhost:11434/v1"));
    }

    @Test
    void missingApiKey_failsFast() {
        assertThrows(IllegalStateException.class, () -> new OpenAiCompatibleProvider(
                new AiProperties("openai", " ", "m", "http://x", 5, 0, 10, 30)));
    }
}
