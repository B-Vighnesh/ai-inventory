package com.stockpulse.api.dto;
import jakarta.validation.constraints.Min;
public record OrderRequest(@Min(1) Integer quantity) {
    public int effectiveQuantity() { return quantity == null ? 1 : quantity; }
}
