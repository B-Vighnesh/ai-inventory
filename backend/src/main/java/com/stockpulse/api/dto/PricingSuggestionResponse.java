package com.stockpulse.api.dto;
import com.stockpulse.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
public record PricingSuggestionResponse(String id, String productId, BigDecimal currentPrice, BigDecimal recommendedPrice, Direction direction,
    double confidence, String reasoning, SuggestionStatus status, TriggerReason triggerReason,
    Instant createdAt, Instant resolvedAt) {
    public static PricingSuggestionResponse from(PricingSuggestion s) {
        return new PricingSuggestionResponse(s.getId(), s.getProduct().getId(), s.getCurrentPrice(), s.getRecommendedPrice(), s.getDirection(),
            s.getConfidence(), s.getReasoning(), s.getStatus(), s.getTriggerReason(), s.getCreatedAt(), s.getResolvedAt());
    }
}
