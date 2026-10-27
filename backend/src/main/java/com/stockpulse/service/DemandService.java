package com.stockpulse.service;

import com.stockpulse.repository.SaleRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DemandService {
    private final SaleRecordRepository sales;
    private final Clock clock;
    public DemandService(SaleRecordRepository sales, Clock clock) { this.sales = sales; this.clock = clock; }
    public int velocity(String id) {
        Instant now = clock.instant();
        return (int) Math.min(Integer.MAX_VALUE, sales.quantity(id, now.minus(Duration.ofHours(24)), now));
    }
    public Map<String, Integer> velocities() {
        Instant now = clock.instant();
        Map<String, Integer> result = new HashMap<>();
        sales.totals(now.minus(Duration.ofHours(24)), now).forEach(row ->
            result.put(row.getProductId(), (int) Math.min(Integer.MAX_VALUE, row.getQuantity())));
        return result;
    }
}
