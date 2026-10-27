package com.stockpulse.events;
public record StockChangedEvent(String productId, int stockLevel, int demandVelocity) {}
// TODO: Add a transactional stock-change listener when the agentic loop is implemented.
