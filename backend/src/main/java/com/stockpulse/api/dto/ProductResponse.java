package com.stockpulse.api.dto;
import com.stockpulse.enums.Category;
import com.stockpulse.enums.ProductStatus;
import com.stockpulse.domain.*;
import java.math.BigDecimal;
public record ProductResponse(String id, String sku, String name, Category category,
    BigDecimal currentPrice, int stockLevel, int reorderThreshold, int demandVelocity,
    ProductStatus status, BigDecimal costPrice, String supplierId) {
    public static ProductResponse from(Product p) { return from(p, p.getDemandVelocity()); }
    public static ProductResponse from(Product p, int demandVelocity) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getCurrentPrice(),
            p.getStockLevel(), p.getReorderThreshold(), demandVelocity, p.getStatus(), p.getCostPrice(), p.getSupplierId());
    }
}
