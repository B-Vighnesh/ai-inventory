package com.stockpulse.service;
import com.stockpulse.repository.*;
import com.stockpulse.api.dto.ProductHistoryResponse;
import com.stockpulse.api.dto.ProductHistoryResponse.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class HistoryService {
    private final ProductRepository products;
    private final InventorySnapshotRepository snapshots;
    private final PriceChangeRepository prices;
    public HistoryService(ProductRepository products, InventorySnapshotRepository snapshots, PriceChangeRepository prices) {
        this.products = products; this.snapshots = snapshots; this.prices = prices;
    }
    public ProductHistoryResponse history(String id) {
        if (!products.existsById(id)) throw ApiException.notFound("Product", id);
        return new ProductHistoryResponse(snapshots.findTop100ByProductIdOrderByCapturedAtDesc(id).stream()
            .map(s -> new Snapshot(s.getId(), s.getStockLevel(), s.getDemandVelocity(), s.getCurrentPrice(), s.getReason(), s.getCapturedAt())).toList(),
            prices.findTop100ByProductIdOrderByChangedAtDesc(id).stream()
            .map(p -> new Price(p.getId(), p.getPreviousPrice(), p.getNewPrice(), p.getSuggestionId(), p.getChangedAt())).toList());
    }
}
