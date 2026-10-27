package com.stockpulse.commerce;

import com.stockpulse.enums.*;
import java.math.BigDecimal;

public record CommerceContext(String productId, String name, Category category, BigDecimal currentPrice,
        int stockLevel, int reorderThreshold, int demandVelocity, double categoryAverage,
        BigDecimal costPrice, TriggerReason triggerReason) {}
