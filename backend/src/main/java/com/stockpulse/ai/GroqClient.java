package com.stockpulse.ai;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

@Component
public class GroqClient {
    private final ObjectMapper json;
    private final String key, baseUrl, model;
    private final Duration timeout;
    private final Clock clock;
    private final int requestsPerMinute;
    private final Deque<Instant> requests = new ArrayDeque<>();
    private Instant blockedUntil = Instant.EPOCH;

    public GroqClient(ObjectMapper json, @Value("${stockpulse.groq.api-key:}") String key,
            @Value("${stockpulse.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${stockpulse.groq.model:openai/gpt-oss-20b}") String model,
            @Value("${stockpulse.groq.timeout:20s}") Duration timeout,
            @Value("${stockpulse.groq.requests-per-minute:4}") int requestsPerMinute, Clock clock) {
        this.json = json; this.key = key; this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model; this.timeout = timeout; this.requestsPerMinute = requestsPerMinute; this.clock = clock;
    }
    public boolean configured() { return !key.isBlank(); }
    private synchronized void acquire() {
        Instant now = clock.instant();
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(now.minusSeconds(60))) requests.removeFirst();
        if (now.isBefore(blockedUntil) || requests.size() >= requestsPerMinute)
            throw new IllegalStateException("Provider budget exhausted");
        requests.addLast(now);
    }
    private synchronized void cooldown(String seconds) {
        long delay = 60;
        try { delay = Math.max(1, Math.min(3600, Long.parseLong(seconds))); } catch (RuntimeException ignored) {}
        blockedUntil = clock.instant().plusSeconds(delay);
    }
    public String complete(String system, String context, Consumer<String> tokens) {
        if (!configured()) throw new IllegalStateException("Provider key is not configured");
        acquire();
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(baseUrl + "/chat/completions");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout((int) Math.min(5000, timeout.toMillis()));
            connection.setReadTimeout((int) timeout.toMillis());
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            Map<String, Object> body = new LinkedHashMap<>();
            if (model.startsWith("openai/gpt-oss")) body.put("reasoning_effort", "low");
            body.put("model", model); body.put("temperature", 0.2); body.put("max_completion_tokens", 1200);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", context)));
            body.put("stream", tokens != null);
            try (OutputStream out = connection.getOutputStream()) { json.writeValue(out, body); }
            int status = connection.getResponseCode();
            if (status == 429) cooldown(connection.getHeaderField("retry-after"));
            if (status != 200) throw new IOException("Provider returned HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                if (tokens == null) {
                    byte[] bytes = input.readNBytes(131073);
                    if (bytes.length > 131072) throw new IOException("Provider response too large");
                    return json.readTree(bytes).path("choices").path(0).path("message").path("content").asText();
                }
                StringBuilder result = new StringBuilder();
                long deadline = System.nanoTime() + timeout.toNanos();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (System.nanoTime() > deadline) throw new IOException("Stream deadline exceeded");
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    JsonNode chunk = json.readTree(data);
                    String token = chunk.path("choices").path(0).path("delta").path("content").asText("");
                    result.append(token);
                    if (result.length() > 16000) throw new IOException("Stream too large");
                    if (!token.isEmpty()) tokens.accept(token);
                }
                return result.toString();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Provider request failed", failure);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
