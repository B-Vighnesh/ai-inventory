package com.stockpulse.repository;
import com.stockpulse.enums.SuggestionStatus;
import com.stockpulse.domain.*;
import org.springframework.data.jpa.repository.*;
public interface PricingSuggestionRepository extends JpaRepository<PricingSuggestion, String>, JpaSpecificationExecutor<PricingSuggestion> {
    java.util.Optional<PricingSuggestion> findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(
        String productId, com.stockpulse.enums.TriggerReason triggerReason, SuggestionStatus status);
    boolean existsByProductIdAndStatus(String productId, SuggestionStatus status);
    java.util.List<PricingSuggestion> findAllByProductIdAndStatus(String productId, SuggestionStatus status);
}
