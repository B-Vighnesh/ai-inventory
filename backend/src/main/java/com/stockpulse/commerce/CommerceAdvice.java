package com.stockpulse.commerce;

import com.stockpulse.enums.Direction;
import java.math.BigDecimal;

public record CommerceAdvice(BigDecimal recommendedPrice, Direction direction, double pricingConfidence,
        String pricingReasoning, int recommendedQuantity, int suggestedLeadTimeDays,
        double reorderConfidence, String reorderReasoning) {}
