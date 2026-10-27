package com.stockpulse.repository;

import com.stockpulse.domain.SaleRecord;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface SaleRecordRepository extends JpaRepository<SaleRecord, String> {
    interface DemandTotal { String getProductId(); Long getQuantity(); }

    @Query("select s.productId as productId, sum(s.quantity) as quantity from SaleRecord s where s.occurredAt > :since and s.occurredAt <= :now group by s.productId")
    List<DemandTotal> totals(@Param("since") Instant since, @Param("now") Instant now);

    @Query("select coalesce(sum(s.quantity), 0) from SaleRecord s where s.productId = :id and s.occurredAt > :since and s.occurredAt <= :now")
    long quantity(@Param("id") String id, @Param("since") Instant since, @Param("now") Instant now);
}
