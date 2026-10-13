package com.lldpractice.lld_practice_platform.llm;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Adapter for the Gemini REST API. Provider types stay inside this class. */
public class GeminiLlmClient implements LlmClient {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient http;

    public GeminiLlmClient(String apiKey, String model, Duration timeout) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) throws LlmException {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", request.system()))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.prompt())))),
                "generationConfig", Map.of("temperature", request.temperature(), "responseMimeType", "application/json"));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmException.Timeout("The AI service did not answer in " + timeout.toSeconds() + " seconds");
        } catch (IOException e) {
            throw new LlmException.Transport("Could not reach the AI service: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException.Timeout("The AI call was interrupted");
        }
        if (resp.statusCode() / 100 != 2) {
            throw new LlmException.Transport("The AI service answered with HTTP " + resp.statusCode(), null);
        }
        return new LlmResponse(extractText(resp.body()));
    }

    static String extractText(String json) throws LlmException {
        try {
            Object root = MAPPER.readValue(json, Object.class);
            Map<?, ?> top = (Map<?, ?>) root;
            Map<?, ?> cand = (Map<?, ?>) ((List<?>) top.get("candidates")).get(0);
            Map<?, ?> content = (Map<?, ?>) cand.get("content");
            Map<?, ?> part = (Map<?, ?>) ((List<?>) content.get("parts")).get(0);
            return String.valueOf(part.get("text"));
        } catch (RuntimeException e) {
            throw new LlmException("The AI service reply had an unexpected shape", e);
        }
    }
}
