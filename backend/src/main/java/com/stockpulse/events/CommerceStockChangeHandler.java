package com.stockpulse.events;

import com.stockpulse.commerce.*;
import com.stockpulse.enums.*;
import com.stockpulse.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@ConditionalOnProperty(name = "stockpulse.commerce.events-enabled", havingValue = "true", matchIfMissing = true)
public class CommerceStockChangeHandler implements StockChangeHandler {
    private final GenerationStore store;
    private final RecommendationService recommendations;
    private final CommerceSettings settings;
    private final ProductRepository products;
    private final boolean initialGeneration;
    public CommerceStockChangeHandler(GenerationStore store, RecommendationService recommendations,
            CommerceSettings settings, ProductRepository products,
            @Value("${stockpulse.commerce.initial-generation:true}") boolean initialGeneration) {
        this.store = store; this.recommendations = recommendations; this.settings = settings;
        this.products = products; this.initialGeneration = initialGeneration;
    }
    @Override
    public void onStockChanged(StockChangedEvent event) {
        CommerceContext context = store.context(event.productId(), TriggerReason.INVENTORY_LOW);
        if (context.stockLevel() < context.reorderThreshold()) queue(context.productId(), TriggerReason.INVENTORY_LOW);
        if (context.demandVelocity() >= 3 && context.demandVelocity() > settings.get().demandSpikeMultiplier() * context.categoryAverage())
            queue(context.productId(), TriggerReason.DEMAND_SPIKE);
    }
    private void queue(String id, TriggerReason reason) {
        try { recommendations.submit(id, reason, SuggestionType.BOTH); }
        catch (RuntimeException failure) {
            // Inventory is already committed; recommendation failures must not turn a saved sale into a 500.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not queue {} review for {} ({})", reason, id, failure.getClass().getSimpleName());
        }
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void created(ProductCreatedEvent event) { if (initialGeneration) queue(event.productId(), TriggerReason.INITIAL); }
    @EventListener(ApplicationReadyEvent.class)
    public void initializeReviewProducts() {
        if (initialGeneration) products.findAll().stream().filter(p -> p.getStatus() == ProductStatus.PRICE_REVIEW_PENDING)
            .forEach(p -> queue(p.getId(), TriggerReason.INITIAL));
    }
}
