package com.stockpulse.api;

import com.stockpulse.ai.GroqClient;
import com.stockpulse.commerce.*;
import com.stockpulse.api.dto.RecommendationJobResponse;
import com.stockpulse.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;

@RestController
public class CommerceController {
    public record SettingsRequest(@NotNull StrategyType strategy, @DecimalMin("1.1") @DecimalMax("20") double demandSpikeMultiplier) {}
    public record SettingsResponse(StrategyType strategy, double demandSpikeMultiplier, List<StrategyType> availableStrategies, boolean groqConfigured) {}
    private final RecommendationService recommendations;
    private final CommerceSettings settings;
    private final GroqClient groq;
    public CommerceController(RecommendationService recommendations, CommerceSettings settings, GroqClient groq) {
        this.recommendations = recommendations; this.settings = settings; this.groq = groq;
    }
    @GetMapping("/settings/commerce")
    public SettingsResponse settings() {
        var state = settings.get();
        return new SettingsResponse(state.strategy(), state.demandSpikeMultiplier(), List.of(StrategyType.values()), groq.configured());
    }
    @PatchMapping("/settings/commerce")
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        settings.update(request.strategy(), request.demandSpikeMultiplier()); return settings();
    }
    @PostMapping("/products/{id}/suggest-pricing")
    public ResponseEntity<RecommendationJobResponse> pricing(@PathVariable String id) { return submit(id, SuggestionType.PRICING); }
    @PostMapping("/products/{id}/suggest-reorder")
    public ResponseEntity<RecommendationJobResponse> reorder(@PathVariable String id) { return submit(id, SuggestionType.REORDER); }
    @PostMapping("/products/{id}/review")
    public ResponseEntity<RecommendationJobResponse> review(@PathVariable String id) { return submit(id, SuggestionType.BOTH); }
    private ResponseEntity<RecommendationJobResponse> submit(String id, SuggestionType type) {
        return ResponseEntity.accepted().body(recommendations.submit(id, TriggerReason.MANUAL, type, true));
    }
    @GetMapping("/recommendation-jobs")
    public List<RecommendationJobResponse> jobs() { return recommendations.recent(); }
    @GetMapping("/recommendation-jobs/{id}")
    public RecommendationJobResponse job(@PathVariable String id) { return recommendations.get(id); }

    @PostMapping(value = "/products/{id}/suggest-pricing/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(60000L);
        var job = recommendations.submit(id, TriggerReason.MANUAL, SuggestionType.PRICING,
            token -> send(emitter, "token", Map.of("text", token)),
            result -> { send(emitter, "result", result); emitter.complete(); }, true);
        send(emitter, "queued", job);
        return emitter;
    }
    private void send(SseEmitter emitter, String event, Object data) {
        try { emitter.send(SseEmitter.event().name(event).data(data)); }
        catch (IOException | IllegalStateException disconnected) { /* Work continues even if the browser disconnects. */ }
    }
}
