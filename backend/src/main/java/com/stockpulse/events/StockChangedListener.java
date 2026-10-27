package com.stockpulse.events;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class StockChangedListener {
    private final ObjectProvider<StockChangeHandler> handlers;

    public StockChangedListener(ObjectProvider<StockChangeHandler> handlers) {
        this.handlers = handlers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        handlers.orderedStream().forEach(handler -> {
            try { handler.onStockChanged(event); }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Stock handler failed for {} ({})", event.productId(), failure.getClass().getSimpleName());
            }
        });
    }
}
