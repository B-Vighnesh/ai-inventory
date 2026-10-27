package com.stockpulse.commerce;

import com.stockpulse.enums.StrategyType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CommerceSettings {
    public record State(StrategyType strategy, double demandSpikeMultiplier) {}
    private final AtomicReference<State> state;
    public CommerceSettings(@Value("${stockpulse.commerce.strategy:AI}") StrategyType strategy,
                            @Value("${stockpulse.commerce.demand-spike-multiplier:3}") double multiplier) {
        state = new AtomicReference<>(new State(strategy, multiplier));
    }
    public State get() { return state.get(); }
    public State update(StrategyType strategy, double multiplier) {
        State next = new State(strategy, multiplier); state.set(next); return next;
    }
}
