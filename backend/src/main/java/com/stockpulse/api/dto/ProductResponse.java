package com.stockpulse.api.dto;
import com.stockpulse.domain.*;
import java.math.BigDecimal;
public record ProductResponse(String id, String sku, String name, Category category,
    BigDecimal currentPrice, int stockLevel, int reorderThreshold, int demandVelocity,
    ProductStatus status, BigDecimal costPrice, String supplierId) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getCurrentPrice(),
            p.getStockLevel(), p.getReorderThreshold(), p.getDemandVelocity(), p.getStatus(), p.getCostPrice(), p.getSupplierId());
    }
}
