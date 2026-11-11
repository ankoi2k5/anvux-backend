package com.anvux.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String provider,
        String apiKey,
        String model,
        String baseUrl,
        int timeoutSeconds,
        int maxRetries,
        int maxPlansPerHour,
        int maxPlansPerDay) {
}