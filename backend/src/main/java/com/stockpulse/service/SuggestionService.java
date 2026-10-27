package com.stockpulse.service;
import com.stockpulse.domain.*;
import com.stockpulse.api.dto.*;
import com.stockpulse.api.dto.ResolveRequest.Action;
import com.stockpulse.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
@Service
@Transactional(readOnly = true)
public class SuggestionService {
    private final PricingSuggestionRepository pricing;
    private final ReorderSuggestionRepository reorders;
    private final ProductRepository products;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public SuggestionService(PricingSuggestionRepository pricing, ReorderSuggestionRepository reorders, ProductRepository products) {
        this.pricing = pricing; this.reorders = reorders; this.products = products;
    }
    private <T> Specification<T> filter(SuggestionStatus status, String productId) {
        return (root, query, cb) -> cb.and(
            status == null ? cb.conjunction() : cb.equal(root.get("status"), status),
            productId == null ? cb.conjunction() : cb.equal(root.get("product").get("id"), productId));
    }
    public List<PricingSuggestionResponse> pricing(SuggestionStatus status, String productId) {
        return pricing.findAll(filter(status, productId), Sort.by("createdAt", "id")).stream().map(PricingSuggestionResponse::from).toList();
    }
    public List<ReorderSuggestionResponse> reorders(SuggestionStatus status, String productId) {
        return reorders.findAll(filter(status, productId), Sort.by("createdAt", "id")).stream().map(ReorderSuggestionResponse::from).toList();
    }
    @Transactional
    public PricingSuggestionResponse resolvePricing(String id, Action action) {
        PricingSuggestion s = pricing.findById(id).orElseThrow(() -> ApiException.notFound("Pricing suggestion", id));
        Product p = lockProduct(s.getProduct().getId());
        // Refresh after acquiring the product lock: another request may have resolved this suggestion.
        entityManager.refresh(s);
        requirePending(s.getStatus());
        if (action == Action.ACCEPT) p.setCurrentPrice(s.getRecommendedPrice());
        s.setStatus(action == Action.ACCEPT ? SuggestionStatus.ACCEPTED : SuggestionStatus.REJECTED);
        s.setResolvedAt(Instant.now()); pricing.saveAndFlush(s);
        clearReview(p);
        return PricingSuggestionResponse.from(s);
    }
    @Transactional
    public ReorderSuggestionResponse resolveReorder(String id, Action action) {
        ReorderSuggestion s = reorders.findById(id).orElseThrow(() -> ApiException.notFound("Reorder suggestion", id));
        Product p = lockProduct(s.getProduct().getId());
        entityManager.refresh(s);
        requirePending(s.getStatus());
        if (action == Action.ACCEPT) {
            if (s.getRecommendedQuantity() > Integer.MAX_VALUE - p.getStockLevel()) throw ApiException.conflict("Stock limit exceeded");
            p.setStockLevel(p.getStockLevel() + s.getRecommendedQuantity());
            if (p.getStatus() == ProductStatus.OUT_OF_STOCK && p.getStockLevel() > 0) p.setStatus(ProductStatus.ACTIVE);
        }
        s.setStatus(action == Action.ACCEPT ? SuggestionStatus.ACCEPTED : SuggestionStatus.REJECTED);
        s.setResolvedAt(Instant.now()); reorders.saveAndFlush(s);
        if (action == Action.REJECT) clearReview(p);
        return ReorderSuggestionResponse.from(s);
    }
    private Product lockProduct(String id) { return products.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Product", id)); }
    private void requirePending(SuggestionStatus status) {
        if (status != SuggestionStatus.PENDING) throw ApiException.conflict("Suggestion has already been resolved");
    }
    private void clearReview(Product p) {
        if (p.getStatus() == ProductStatus.PRICE_REVIEW_PENDING && !pricing.existsByProductIdAndStatus(p.getId(), SuggestionStatus.PENDING))
            p.setStatus(ProductStatus.ACTIVE);
    }
}
