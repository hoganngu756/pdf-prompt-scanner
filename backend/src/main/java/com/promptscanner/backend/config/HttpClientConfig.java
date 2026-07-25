package com.promptscanner.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    /**
     * A bare {@code new RestTemplate()} has no connect or read timeout, so a hung
     * upstream call pins a request thread indefinitely. These bounds keep a slow
     * Gemini response from exhausting the servlet thread pool.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }

    // NOTE: deliberately no ObjectMapper bean here. Declaring one makes Spring Boot
    // back off its auto-configured mapper, which is the only one that registers
    // JavaTimeModule -- without it ScanRecord.scanDate (LocalDateTime) fails to
    // serialize and GET /api/history returns 500.
}
