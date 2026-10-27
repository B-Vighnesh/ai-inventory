package com.stockpulse.repository;
import com.stockpulse.enums.SuggestionStatus;
import com.stockpulse.domain.*;
import org.springframework.data.jpa.repository.*;
public interface PricingSuggestionRepository extends JpaRepository<PricingSuggestion, String>, JpaSpecificationExecutor<PricingSuggestion> {
    boolean existsByProductIdAndStatus(String productId, SuggestionStatus status);
}
