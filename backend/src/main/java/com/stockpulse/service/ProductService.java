package com.stockpulse.service;
import com.stockpulse.enums.Category;
import com.stockpulse.enums.ProductStatus;
import com.stockpulse.domain.*;
import com.stockpulse.api.dto.*;
import com.stockpulse.events.StockChangedEvent;
import com.stockpulse.repository.*;
import com.stockpulse.enums.InventoryChange;
import com.stockpulse.events.ProductCreatedEvent;
import java.time.Clock;
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
    private final DemandService demand;
    private final SaleRecordRepository sales;
    private final InventorySnapshotRepository snapshots;
    private final Clock clock;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public ProductService(ProductRepository products, ApplicationEventPublisher events, DemandService demand,
                          SaleRecordRepository sales, InventorySnapshotRepository snapshots, Clock clock) {
        this.products = products; this.events = events; this.demand = demand;
        this.sales = sales; this.snapshots = snapshots; this.clock = clock;
    }
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
        if (p.getDemandVelocity() > 0) sales.save(new SaleRecord(p.getId(), p.getDemandVelocity(), clock.instant()));
        snapshots.save(new InventorySnapshot(p, p.getDemandVelocity(), InventoryChange.INITIAL, clock.instant()));
        events.publishEvent(new ProductCreatedEvent(p.getId()));
        return ProductResponse.from(p);
    }
    public List<ProductResponse> list(ProductStatus status, Category category) {
        Specification<Product> filter = (root, query, cb) -> cb.and(
            status == null ? cb.conjunction() : cb.equal(root.get("status"), status),
            category == null ? cb.conjunction() : cb.equal(root.get("category"), category));
        var velocities = demand.velocities();
        return products.findAll(filter, Sort.by("id")).stream().map(p -> ProductResponse.from(p, velocities.getOrDefault(p.getId(), 0))).toList();
    }
    public ProductResponse get(String id) { return ProductResponse.from(products.findById(id).orElseThrow(() -> ApiException.notFound("Product", id)), demand.velocity(id)); }
    @Transactional
    public ProductResponse setStock(String id, int stock) {
        Product p = locked(id); p.setStockLevel(stock); p.setDemandVelocity(demand.velocity(id));
        updateStockStatus(p); return saveAndPublish(p, InventoryChange.STOCK_SET);
    }
    @Transactional
    public ProductResponse order(String id, int quantity) {
        Product p = locked(id);
        p.setDemandVelocity(demand.velocity(id));
        if (quantity > p.getStockLevel()) throw ApiException.conflict("Quantity exceeds available stock");
        if (quantity > Integer.MAX_VALUE - p.getDemandVelocity()) throw ApiException.conflict("Demand velocity limit exceeded");
        p.setStockLevel(p.getStockLevel() - quantity); p.setDemandVelocity(p.getDemandVelocity() + quantity);
        sales.save(new SaleRecord(id, quantity, clock.instant()));
        updateStockStatus(p); return saveAndPublish(p, InventoryChange.ORDER);
    }
    private Product locked(String id) { return products.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Product", id)); }
    private void updateStockStatus(Product p) {
        if (p.getStockLevel() == 0) p.setStatus(ProductStatus.OUT_OF_STOCK);
        else if (p.getStatus() == ProductStatus.OUT_OF_STOCK) p.setStatus(ProductStatus.ACTIVE);
    }
    private ProductResponse saveAndPublish(Product p, InventoryChange reason) {
        products.saveAndFlush(p);
        snapshots.save(new InventorySnapshot(p, p.getDemandVelocity(), reason, clock.instant()));
        events.publishEvent(new StockChangedEvent(p.getId(), p.getStockLevel(), p.getDemandVelocity()));
        return ProductResponse.from(p);
    }
}
