package com.stockpulse.api.dto;
import com.stockpulse.domain.Category;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record CreateProductRequest(
    @NotBlank @Size(max = 100) String id,
    @NotBlank @Size(max = 100) String sku,
    @NotBlank @Size(max = 255) String name,
    @NotNull Category category,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal currentPrice,
    @NotNull @Min(0) Integer stockLevel,
    @NotNull @Min(0) Integer reorderThreshold,
    @Min(0) Integer demandVelocity,
    @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal costPrice,
    @Size(max = 100) String supplierId) {}
