package com.stockpulse.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity
public class ReorderSuggestion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(nullable = false) @jakarta.validation.constraints.Min(0)
    private int currentStock;
    @Column(nullable = false) @jakarta.validation.constraints.Min(1)
    private int recommendedQuantity;
    @Column(nullable = false) @jakarta.validation.constraints.Min(0)
    private int suggestedLeadTimeDays;
    @Column(nullable = false) @jakarta.validation.constraints.DecimalMin("0.0") @jakarta.validation.constraints.DecimalMax("1.0")
    private double confidence;
    @Column(nullable = false, columnDefinition = "text")
    private String reasoning;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private SuggestionStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TriggerReason triggerReason;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant resolvedAt;
    public ReorderSuggestion() {}
    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = SuggestionStatus.PENDING;
        if (triggerReason == null) triggerReason = TriggerReason.INITIAL;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getCurrentStock() { return currentStock; }
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }
    public int getRecommendedQuantity() { return recommendedQuantity; }
    public void setRecommendedQuantity(int recommendedQuantity) { this.recommendedQuantity = recommendedQuantity; }
    public int getSuggestedLeadTimeDays() { return suggestedLeadTimeDays; }
    public void setSuggestedLeadTimeDays(int suggestedLeadTimeDays) { this.suggestedLeadTimeDays = suggestedLeadTimeDays; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public SuggestionStatus getStatus() { return status; }
    public void setStatus(SuggestionStatus status) { this.status = status; }
    public TriggerReason getTriggerReason() { return triggerReason; }
    public void setTriggerReason(TriggerReason triggerReason) { this.triggerReason = triggerReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
