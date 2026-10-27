package com.stockpulse.api.dto;

import com.stockpulse.enums.*;
import java.time.Instant;

public record RecommendationJobResponse(String id, String productId, TriggerReason triggerReason,
        SuggestionType type, JobStatus status, RecommendationSource source, String message,
        String pricingId, String reorderId, Instant createdAt, Instant updatedAt) {}
