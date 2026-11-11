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


@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
    private static final int MAX_TOKENS = 4000; // mô hình có "thinking" tiêu tốn token cho phần suy luận

    private final AiProperties props;
    private final RestClient client;
    private final String label;

    public OpenAiCompatibleProvider(AiProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Thiếu AI_API_KEY: hãy đặt biến môi trường này, hoặc dùng AI_PROVIDER=fake");
        }
        if (props.baseUrl() == null || props.baseUrl().isBlank()) {
            throw new IllegalStateException("Thiếu AI_BASE_URL cho AI_PROVIDER=openai");
        }
        this.props = props;
        this.label = labelFor(props.baseUrl());

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));

        this.client = RestClient.builder()
                .baseUrl(stripTrailingSlash(props.baseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();
    }

    @Override
    public String name() {
        return label;
    }

    @Override
    public AiPlanResult generatePlan(AiPlanRequest request) {
        boolean jsonMode = true;
        boolean repaired = false;
        String userMessage = AiPrompts.buildUserMessage(request);

        for (int attempt = 0; ; attempt++) {
            try {
                Response response = post(buildBody(userMessage, jsonMode));
                try {
                    AiPlanDraft draft = AiJsonParser.parse(extractContent(response));
                    return new AiPlanResult(draft, response.model() == null ? props.model() : response.model());
                } catch (AiProviderException invalid) {
                    if (repaired) {
                        throw invalid;
                    }
                    // Hỏi lại đúng một lần, nhắc rõ định dạng; không tính vào số lần thử mạng.
                    repaired = true;
                    userMessage = userMessage + "\n\n" + AiPrompts.REPAIR_REMINDER;
                    log.warn("AI trả về nội dung không đọc được, hỏi lại một lần");
                    attempt--;
                }
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                log.warn("Gọi AI thất bại: HTTP {} (lần {}): {}", status, attempt + 1,
                        abbreviate(e.getResponseBodyAsString(), 300));
                if (status == 400 && jsonMode) {
                    // Một số mô hình/nhà cung cấp không hỗ trợ response_format: thử lại không có nó.
                    jsonMode = false;
                    attempt--;
                    continue;
                }
                if (!isRetryable(status) || attempt >= props.maxRetries()) {
                    throw status == 429 ? AiProviderException.busy() : AiProviderException.unavailable();
                }
                sleepBeforeRetry(attempt, e);
            } catch (ResourceAccessException e) {
                log.warn("Gọi AI thất bại do mạng hoặc quá thời gian (lần {})", attempt + 1);
                if (attempt >= props.maxRetries()) {
                    throw AiProviderException.unavailable();
                }
                sleepBeforeRetry(attempt, null);
            }
        }
    }

    private Response post(Map<String, Object> body) {
        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Response.class);
    }

    private Map<String, Object> buildBody(String userMessage, boolean jsonMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("temperature", 0.3);
        body.put("max_tokens", MAX_TOKENS);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", AiPrompts.SYSTEM_PROMPT_JSON),
                Map.of("role", "user", "content", userMessage)));
        return body;
    }

    private static String extractContent(Response response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw AiProviderException.invalidOutput();
        }
        return response.choices().get(0).message().content();
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    /** Gói miễn phí hay giới hạn số yêu cầu mỗi phút: tôn trọng Retry-After nhưng không chờ quá 10 giây. */
    private static void sleepBeforeRetry(int attempt, RestClientResponseException error) {
        long millis = 1000L * (attempt + 1);
        if (error != null && error.getResponseHeaders() != null) {
            String retryAfter = error.getResponseHeaders().getFirst("Retry-After");
            if (retryAfter != null) {
                try {
                    millis = Math.max(millis, Long.parseLong(retryAfter.trim()) * 1000L);
                } catch (NumberFormatException ignored) {
                    // giữ thời gian chờ mặc định
                }
            }
        }
        try {
            Thread.sleep(Math.min(millis, 10_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiProviderException.unavailable();
        }
    }

    static String labelFor(String baseUrl) {
        String url = baseUrl.toLowerCase();
        if (url.contains("generativelanguage.googleapis.com")) {
            return "gemini";
        }
        if (url.contains("groq.com")) {
            return "groq";
        }
        if (url.contains("openrouter.ai")) {
            return "openrouter";
        }
        return "openai-compat";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String model, List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }
}
