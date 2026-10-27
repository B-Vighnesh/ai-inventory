package com.stockpulse.repository;
import com.stockpulse.domain.PriceChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PriceChangeRepository extends JpaRepository<PriceChange, String> {
    List<PriceChange> findTop100ByProductIdOrderByChangedAtDesc(String productId);
}
