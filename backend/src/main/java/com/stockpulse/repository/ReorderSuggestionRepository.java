package com.stockpulse.repository;
import com.stockpulse.domain.*;
import org.springframework.data.jpa.repository.*;
public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, String>, JpaSpecificationExecutor<ReorderSuggestion> {
    boolean existsByProductIdAndStatus(String productId, SuggestionStatus status);
}
