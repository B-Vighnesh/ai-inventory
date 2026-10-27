package com.stockpulse.events;

/** Implement as a Spring bean to process committed stock changes in the agentic loop. */
@FunctionalInterface
public interface StockChangeHandler {
    void onStockChanged(StockChangedEvent event);
}
