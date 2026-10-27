package com.stockpulse.domain;

import com.stockpulse.enums.InventoryChange;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(indexes = @Index(name = "idx_snapshot_product_time", columnList = "product_id,capturedAt"))
public class InventorySnapshot {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(name = "product_id", nullable = false)
    private String productId;
    private int stockLevel;
    private int demandVelocity;
    @Column(precision = 19, scale = 2)
    private BigDecimal currentPrice;
    @Enumerated(EnumType.STRING)
    private InventoryChange reason;
    private Instant capturedAt;

    protected InventorySnapshot() {}
    public InventorySnapshot(Product product, int velocity, InventoryChange reason, Instant at) {
        this.productId = product.getId();
        this.stockLevel = product.getStockLevel();
        this.demandVelocity = velocity;
        this.currentPrice = product.getCurrentPrice();
        this.reason = reason;
        this.capturedAt = at;
    }
    public String getId() { return id; }
    public String getProductId() { return productId; }
    public int getStockLevel() { return stockLevel; }
    public int getDemandVelocity() { return demandVelocity; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public InventoryChange getReason() { return reason; }
    public Instant getCapturedAt() { return capturedAt; }
}
