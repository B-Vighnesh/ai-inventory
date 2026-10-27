package com.stockpulse.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(indexes = @Index(name = "idx_price_product_time", columnList = "product_id,changedAt"))
public class PriceChange {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(name = "product_id", nullable = false)
    private String productId;
    @Column(precision = 19, scale = 2)
    private BigDecimal previousPrice;
    @Column(precision = 19, scale = 2)
    private BigDecimal newPrice;
    private String suggestionId;
    private Instant changedAt;

    protected PriceChange() {}
    public PriceChange(String productId, BigDecimal previousPrice, BigDecimal newPrice, String suggestionId, Instant at) {
        this.productId = productId;
        this.previousPrice = previousPrice;
        this.newPrice = newPrice;
        this.suggestionId = suggestionId;
        this.changedAt = at;
    }
    public String getId() { return id; }
    public BigDecimal getPreviousPrice() { return previousPrice; }
    public BigDecimal getNewPrice() { return newPrice; }
    public String getSuggestionId() { return suggestionId; }
    public Instant getChangedAt() { return changedAt; }
}
