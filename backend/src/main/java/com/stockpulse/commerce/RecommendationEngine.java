package com.stockpulse.commerce;

import com.stockpulse.enums.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Consumer;

@Service
public class RecommendationEngine {
    public record Result(CommerceAdvice advice, RecommendationSource source, String note) {}
    private final Map<StrategyType, CommerceAdvisor> strategies = new EnumMap<>(StrategyType.class);
    private final CommerceSettings settings;
    private final RuleBasedAdvisor baseline;
    private final AdviceValidator validator;

    public RecommendationEngine(List<CommerceAdvisor> advisors, CommerceSettings settings,
                                RuleBasedAdvisor baseline, AdviceValidator validator) {
        advisors.forEach(advisor -> strategies.put(advisor.type(), advisor));
        this.settings = settings; this.baseline = baseline; this.validator = validator;
    }
    public Result recommend(CommerceContext context, Consumer<String> tokenSink) {
        StrategyType type = settings.get().strategy();
        if (type == StrategyType.RULE_BASED)
            return new Result(baseline.advise(context, null), RecommendationSource.RULE_BASED, "Deterministic rules selected");
        try {
            CommerceAdvice result = strategies.get(type).advise(context, tokenSink);
            validator.validate(context, result);
            return new Result(result, RecommendationSource.AI, "AI response validated");
        } catch (RuntimeException failure) {
            // Provider response bodies and credentials are deliberately not copied into persisted errors.
            return fallback(context, "AI unavailable or invalid; deterministic rules applied");
        }
    }
    public Result fallback(CommerceContext context, String note) {
        return new Result(baseline.advise(context, null), RecommendationSource.RULE_BASED_FALLBACK, note);
    }
}
