package com.stockpulse.repository;
import com.stockpulse.enums.SuggestionStatus;
import com.stockpulse.domain.*;
import org.springframework.data.jpa.repository.*;
public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, String>, JpaSpecificationExecutor<ReorderSuggestion> {
    java.util.Optional<ReorderSuggestion> findFirstByProductIdAndTriggerReasonAndStatusOrderByCreatedAtDesc(
        String productId, com.stockpulse.enums.TriggerReason triggerReason, SuggestionStatus status);
    boolean existsByProductIdAndStatus(String productId, SuggestionStatus status);
}
