package com.stockpulse.events;
public record StockChangedEvent(String productId, int stockLevel, int demandVelocity) {}
