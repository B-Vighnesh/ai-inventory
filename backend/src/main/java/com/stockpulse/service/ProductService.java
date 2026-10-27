package com.stockpulse.service;
import com.stockpulse.domain.*;
import com.stockpulse.api.dto.*;
import com.stockpulse.events.StockChangedEvent;
import com.stockpulse.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;
    private final ApplicationEventPublisher events;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public ProductService(ProductRepository products, ApplicationEventPublisher events) { this.products = products; this.events = events; }
    @Transactional
    public ProductResponse create(CreateProductRequest r) {
        if (products.existsById(r.id())) throw ApiException.conflict("Product ID already exists");
        Product p = new Product();
        p.setId(r.id()); p.setSku(r.sku()); p.setName(r.name()); p.setCategory(r.category());
        p.setCurrentPrice(r.currentPrice()); p.setStockLevel(r.stockLevel()); p.setReorderThreshold(r.reorderThreshold());
        p.setDemandVelocity(r.demandVelocity() == null ? 0 : r.demandVelocity());
        p.setStatus(r.stockLevel() == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE);
        p.setCostPrice(r.costPrice()); p.setSupplierId(r.supplierId());
        // Persist assigned IDs as inserts so a concurrent creation cannot merge over an existing product.
        entityManager.persist(p);
        products.flush();
        return ProductResponse.from(p);
    }
    public List<ProductResponse> list(ProductStatus status, Category category) {
        Specification<Product> filter = (root, query, cb) -> cb.and(
            status == null ? cb.conjunction() : cb.equal(root.get("status"), status),
            category == null ? cb.conjunction() : cb.equal(root.get("category"), category));
        return products.findAll(filter, Sort.by("id")).stream().map(ProductResponse::from).toList();
    }
    public ProductResponse get(String id) { return ProductResponse.from(products.findById(id).orElseThrow(() -> ApiException.notFound("Product", id))); }
    @Transactional
    public ProductResponse setStock(String id, int stock) {
        Product p = locked(id); p.setStockLevel(stock); updateStockStatus(p); return saveAndPublish(p);
    }
    @Transactional
    public ProductResponse order(String id, int quantity) {
        Product p = locked(id);
        if (quantity > p.getStockLevel()) throw ApiException.conflict("Quantity exceeds available stock");
        if (quantity > Integer.MAX_VALUE - p.getDemandVelocity()) throw ApiException.conflict("Demand velocity limit exceeded");
        p.setStockLevel(p.getStockLevel() - quantity); p.setDemandVelocity(p.getDemandVelocity() + quantity);
        updateStockStatus(p); return saveAndPublish(p);
    }
    private Product locked(String id) { return products.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Product", id)); }
    private void updateStockStatus(Product p) {
        if (p.getStockLevel() == 0) p.setStatus(ProductStatus.OUT_OF_STOCK);
        else if (p.getStatus() == ProductStatus.OUT_OF_STOCK) p.setStatus(ProductStatus.ACTIVE);
    }
    private ProductResponse saveAndPublish(Product p) {
        products.saveAndFlush(p);
        events.publishEvent(new StockChangedEvent(p.getId(), p.getStockLevel(), p.getDemandVelocity()));
        return ProductResponse.from(p);
    }
}
