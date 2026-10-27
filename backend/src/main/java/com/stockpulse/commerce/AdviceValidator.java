package com.stockpulse.commerce;

import com.stockpulse.enums.Direction;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class AdviceValidator {
    public void validate(CommerceContext c, CommerceAdvice a) {
        if (a == null || a.recommendedPrice() == null || a.recommendedPrice().signum() <= 0
                || a.recommendedPrice().scale() > 2 || a.recommendedPrice().precision() > 19
                || a.recommendedPrice().compareTo(c.currentPrice().multiply(new BigDecimal("0.5"))) < 0
                || a.recommendedPrice().compareTo(c.currentPrice().multiply(new BigDecimal("1.5"))) > 0)
            throw new IllegalArgumentException("Recommended price is outside the allowed range");
        int delta = a.recommendedPrice().compareTo(c.currentPrice());
        Direction expected = delta > 0 ? Direction.INCREASE : delta < 0 ? Direction.DECREASE : Direction.HOLD;
        if (a.direction() != expected) throw new IllegalArgumentException("Price direction does not match the recommendation");
        if (a.recommendedQuantity() < 1 || a.recommendedQuantity() > 1_000_000
                || a.suggestedLeadTimeDays() < 0 || a.suggestedLeadTimeDays() > 365)
            throw new IllegalArgumentException("Invalid reorder quantity or lead time");
        if (!confidence(a.pricingConfidence()) || !confidence(a.reorderConfidence()))
            throw new IllegalArgumentException("Confidence must be between zero and one");
        if (!reasoning(a.pricingReasoning()) || !reasoning(a.reorderReasoning()))
            throw new IllegalArgumentException("Reasoning must be present and bounded");
    }
    private boolean confidence(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    private boolean reasoning(String value) { return value != null && !value.isBlank() && value.length() <= 4000; }
}
