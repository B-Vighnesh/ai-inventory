package com.stockpulse.api.dto;
import com.stockpulse.enums.SuggestionStatus;
import com.stockpulse.enums.TriggerReason;
import com.stockpulse.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
public record ReorderSuggestionResponse(String id, String productId, int currentStock, int recommendedQuantity, int suggestedLeadTimeDays,
    double confidence, String reasoning, SuggestionStatus status, TriggerReason triggerReason,
    Instant createdAt, Instant resolvedAt, com.stockpulse.enums.RecommendationSource source, String generationNote) {
    public static ReorderSuggestionResponse from(ReorderSuggestion s) {
        return new ReorderSuggestionResponse(s.getId(), s.getProduct().getId(), s.getCurrentStock(), s.getRecommendedQuantity(), s.getSuggestedLeadTimeDays(),
            s.getConfidence(), s.getReasoning(), s.getStatus(), s.getTriggerReason(), s.getCreatedAt(), s.getResolvedAt(), s.getSource(), s.getGenerationNote());
    }
}
