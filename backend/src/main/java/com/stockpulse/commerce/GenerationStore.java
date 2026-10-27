package com.stockpulse.commerce;

import com.stockpulse.domain.*;
import com.stockpulse.enums.*;
import com.stockpulse.repository.*;
import com.stockpulse.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
public class GenerationStore {
    public record Saved(String pricingId, String reorderId, RecommendationSource source, String note) {}
    private final ProductRepository products;
    private final PricingSuggestionRepository pricing;
    private final ReorderSuggestionRepository reorders;
    private final DemandService demand;
    private final RecommendationEngine engine;

    public GenerationStore(ProductRepository products, PricingSuggestionRepository pricing,
            ReorderSuggestionRepository reorders, DemandService demand, RecommendationEngine engine) {
        this.products = products; this.pricing = pricing; this.reorders = reorders; this.demand = demand; this.engine = engine;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CommerceContext context(String id, TriggerReason trigger) {
        return context(products.findById(id).orElseThrow(() -> ApiException.notFound("Product", id)), trigger);
    }
    private CommerceContext context(Product p, TriggerReason trigger) {
        Map<String, Integer> velocities = demand.velocities();
        double peerAverage = products.findAll().stream()
            .filter(peer -> peer.getCategory() == p.getCategory() && !peer.getId().equals(p.getId()))
            .mapToInt(peer -> velocities.getOrDefault(peer.getId(), 0)).average().orElse(0);
        return new CommerceContext(p.getId(), p.getName(), p.getCategory(), p.getCurrentPrice(), p.getStockLevel(),
            p.getReorderThreshold(), velocities.getOrDefault(p.getId(), 0), peerAverage, p.getCostPrice(), trigger);
    }
    @Transactional(readOnly = true)
    public Optional<Saved> existing(String id, TriggerReason trigger, SuggestionType type) {
        var price = pricing.findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(id, trigger, SuggestionStatus.PENDING);
        var reorder = reorders.findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(id, trigger, SuggestionStatus.PENDING);
        if ((type != SuggestionType.REORDER && price.isEmpty()) || (type != SuggestionType.PRICING && reorder.isEmpty())) return Optional.empty();
        return Optional.of(new Saved(price.map(PricingSuggestion::getId).orElse(null), reorder.map(ReorderSuggestion::getId).orElse(null),
            price.map(PricingSuggestion::getSource).orElseGet(() -> reorder.orElseThrow().getSource()), "Existing pending recommendations reused"));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Saved save(CommerceContext requested, RecommendationEngine.Result result, SuggestionType type) {
        Product p = products.findForUpdate(requested.productId()).orElseThrow(() -> ApiException.notFound("Product", requested.productId()));
        CommerceContext latest = context(p, requested.triggerReason());
        // Never persist AI advice based on stock, price, or demand that changed while the model was running.
        if (!latest.equals(requested)) result = engine.fallback(latest, "Inventory changed during review; rules recalculated on current data");
        CommerceAdvice advice = result.advice();
        var price = pricing.findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(p.getId(), requested.triggerReason(), SuggestionStatus.PENDING);
        var reorder = reorders.findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(p.getId(), requested.triggerReason(), SuggestionStatus.PENDING);
        String pricingId = null, reorderId = null;
        if (type != SuggestionType.REORDER) {
            PricingSuggestion s = price.orElseGet(PricingSuggestion::new);
            if (price.isEmpty()) {
                s.setProduct(p); s.setCurrentPrice(p.getCurrentPrice()); s.setRecommendedPrice(advice.recommendedPrice());
                s.setDirection(advice.direction()); s.setConfidence(advice.pricingConfidence()); s.setReasoning(advice.pricingReasoning());
                s.setTriggerReason(requested.triggerReason()); s.setSource(result.source()); s.setGenerationNote(result.note());
                pricing.save(s);
            }
            pricingId = s.getId();
            if (p.getStatus() == ProductStatus.ACTIVE) p.setStatus(ProductStatus.PRICE_REVIEW_PENDING);
        }
        if (type != SuggestionType.PRICING) {
            ReorderSuggestion s = reorder.orElseGet(ReorderSuggestion::new);
            if (reorder.isEmpty()) {
                s.setProduct(p); s.setCurrentStock(p.getStockLevel()); s.setRecommendedQuantity(advice.recommendedQuantity());
                s.setSuggestedLeadTimeDays(advice.suggestedLeadTimeDays()); s.setConfidence(advice.reorderConfidence());
                s.setReasoning(advice.reorderReasoning()); s.setTriggerReason(requested.triggerReason());
                s.setSource(result.source()); s.setGenerationNote(result.note()); reorders.save(s);
            }
            reorderId = s.getId();
        }
        return new Saved(pricingId, reorderId, result.source(), result.note());
    }
}
