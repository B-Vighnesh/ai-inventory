package com.stockpulse.commerce;

import com.stockpulse.enums.StrategyType;
import java.util.function.Consumer;

/** Both manual requests and event jobs use this contract. */
public interface CommerceAdvisor {
    StrategyType type();
    CommerceAdvice advise(CommerceContext context, Consumer<String> tokenSink);
}
