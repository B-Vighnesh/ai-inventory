package com.stockpulse.api.dto;
import com.stockpulse.enums.InventoryChange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record ProductHistoryResponse(List<Snapshot> inventory, List<Price> prices) {
    public record Snapshot(String id, int stockLevel, int demandVelocity, BigDecimal currentPrice, InventoryChange reason, Instant capturedAt) {}
    public record Price(String id, BigDecimal previousPrice, BigDecimal newPrice, String suggestionId, Instant changedAt) {}
}
