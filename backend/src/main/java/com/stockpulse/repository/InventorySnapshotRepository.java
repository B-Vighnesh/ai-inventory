package com.stockpulse.repository;
import com.stockpulse.domain.InventorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, String> {
    List<InventorySnapshot> findTop100ByProductIdOrderByCapturedAtDesc(String productId);
}
