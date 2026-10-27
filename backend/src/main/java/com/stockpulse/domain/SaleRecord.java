package com.stockpulse.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(indexes = @Index(name = "idx_sale_product_time", columnList = "product_id,occurredAt"))
public class SaleRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(name = "product_id", nullable = false)
    private String productId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private Instant occurredAt;

    protected SaleRecord() {}
    public SaleRecord(String productId, int quantity, Instant occurredAt) {
        this.productId = productId;
        this.quantity = quantity;
        this.occurredAt = occurredAt;
    }
    public String getId() { return id; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Instant getOccurredAt() { return occurredAt; }
}
