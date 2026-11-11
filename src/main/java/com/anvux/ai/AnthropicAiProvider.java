package com.anvux.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.json.JsonMapper;

/**
 * Gọi API Messages của Anthropic. Dùng "tool use" bắt buộc để mô hình trả về JSON theo lược đồ cố định,
 * thay vì văn bản tự do cần đoán và cắt.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic")
public class AnthropicAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiProvider.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_TOKENS = 2000;

    private final AiProperties props;
    private final RestClient client;
    private final Map<String, Object> planSchema;

    public AnthropicAiProvider(AiProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Thiếu ANTHROPIC_API_KEY: hãy đặt biến môi trường này, hoặc dùng AI_PROVIDER=fake");
        }
        this.props = props;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));

        this.client = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        this.planSchema = parseSchema();
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public AiPlanResult generatePlan(AiPlanRequest request) {
        Map<String, Object> body = buildBody(request);

        for (int attempt = 0; ; attempt++) {
            try {
                return parse(post(body));
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                log.warn("Gọi AI thất bại: HTTP {} (lần {}): {}", status, attempt + 1,
                        abbreviate(e.getResponseBodyAsString(), 300));
                if (!isRetryable(status) || attempt >= props.maxRetries()) {
                    throw AiProviderException.unavailable();
                }
            } catch (ResourceAccessException e) {
                log.warn("Gọi AI thất bại do mạng hoặc quá thời gian (lần {})", attempt + 1);
                if (attempt >= props.maxRetries()) {
                    throw AiProviderException.unavailable();
                }
            }
            sleepBeforeRetry(attempt);
        }
    }

    private Response post(Map<String, Object> body) {
        return client.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Response.class);
    }

    private Map<String, Object> buildBody(AiPlanRequest request) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", AiPrompts.TOOL_NAME);
        tool.put("description", "Gửi kế hoạch hành động đã lập để người dùng xem và duyệt.");
        tool.put("input_schema", planSchema);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", AiPrompts.SYSTEM_PROMPT);
        body.put("tools", List.of(tool));
        body.put("tool_choice", Map.of("type", "tool", "name", AiPrompts.TOOL_NAME));
        body.put("messages", List.of(Map.of("role", "user", "content", AiPrompts.buildUserMessage(request))));
        return body;
    }

    private AiPlanResult parse(Response response) {
        if (response == null || response.content() == null) {
            throw AiProviderException.invalidOutput();
        }
        Block block = response.content().stream()
                .filter(b -> "tool_use".equals(b.type()) && AiPrompts.TOOL_NAME.equals(b.name()))
                .findFirst()
                .orElseThrow(AiProviderException::invalidOutput);
        try {
            AiPlanDraft draft = MAPPER.convertValue(block.input(), AiPlanDraft.class);
            return new AiPlanResult(draft, response.model() == null ? props.model() : response.model());
        } catch (RuntimeException e) {
            log.warn("Không đọc được kết quả của AI: {}", e.getClass().getSimpleName());
            throw AiProviderException.invalidOutput();
        }
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(1000L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiProviderException.unavailable();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseSchema() {
        return MAPPER.readValue(AiPrompts.PLAN_SCHEMA_JSON, Map.class);
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String model, List<Block> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Block(String type, String name, Map<String, Object> input) {
    }
}