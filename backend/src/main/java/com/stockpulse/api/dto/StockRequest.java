package com.stockpulse.api.dto;
import jakarta.validation.constraints.*;
public record StockRequest(@NotNull @Min(0) Integer stockLevel) {}
