package com.stockpulse.commerce;

import com.stockpulse.enums.*;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.function.Consumer;

@Component
public class RuleBasedAdvisor implements CommerceAdvisor {
    @Override public StrategyType type() { return StrategyType.RULE_BASED; }
    @Override public CommerceAdvice advise(CommerceContext c, Consumer<String> tokenSink) {
        BigDecimal multiplier = BigDecimal.ONE;
        String reason = "Stock is above its reorder threshold and demand is within the peer range; hold the price.";
        if (c.stockLevel() < c.reorderThreshold()) {
            multiplier = new BigDecimal("1.10");
            reason = "Stock is below the reorder threshold. A 10% increase protects remaining inventory while replenishment is reviewed.";
        } else if (c.categoryAverage() > 0 && c.demandVelocity() > 2 * c.categoryAverage()) {
            multiplier = new BigDecimal("1.05");
            reason = "Demand exceeds twice the category peer average. A 5% increase balances margin with continued sales.";
        }
        BigDecimal price = c.currentPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        // Database and order APIs use signed 32-bit stock counts.
        long target = Math.max(1L, 3L * c.reorderThreshold() - c.stockLevel());
        int quantity = (int) Math.min(target, Integer.MAX_VALUE);
        return new CommerceAdvice(price, price.compareTo(c.currentPrice()) > 0 ? Direction.INCREASE : Direction.HOLD,
            0.75, reason, quantity, 7, 0.70,
            "Restore a target buffer of three times the reorder threshold. Recommended quantity: " + quantity + "; assumed lead time: 7 days.");
    }
}
