package com.lldpractice.lld_practice_platform.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {
    @Bean
    public LlmClient llmClient(@Value("${lld.llm.gemini.api-key:}") String apiKey,
                               @Value("${lld.llm.gemini.model:gemini-2.5-flash}") String model,
                               @Value("${lld.llm.timeout-seconds:60}") long timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            return new DemoLlmClient();
        }
        return new GeminiLlmClient(apiKey, model, Duration.ofSeconds(timeoutSeconds));
    }
}
