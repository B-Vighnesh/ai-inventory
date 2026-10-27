package com.stockpulse.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity
public class Product {
    @Id @Column(length = 100)
    private String id;
    @Column(nullable = false, unique = true, length = 100)
    private String sku;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Category category;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPrice;
    @Column(nullable = false)
    private int stockLevel;
    @Column(nullable = false)
    private int reorderThreshold;
    @Column(nullable = false)
    private int demandVelocity;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ProductStatus status;
    // Sprint 2: supplier cost for margin calculations.
    @Column(precision = 19, scale = 2)
    private BigDecimal costPrice;
    // Sprint 2: reference to the supplier domain.
    @Column(length = 100)
    private String supplierId;
    public Product() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public int getStockLevel() { return stockLevel; }
    public void setStockLevel(int stockLevel) { this.stockLevel = stockLevel; }
    public int getReorderThreshold() { return reorderThreshold; }
    public void setReorderThreshold(int reorderThreshold) { this.reorderThreshold = reorderThreshold; }
    public int getDemandVelocity() { return demandVelocity; }
    public void setDemandVelocity(int demandVelocity) { this.demandVelocity = demandVelocity; }
    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }
}
