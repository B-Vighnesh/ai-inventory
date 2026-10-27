package com.stockpulse;

import com.stockpulse.ai.*;
import com.stockpulse.commerce.*;
import com.stockpulse.enums.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommerceAdvisorTests {
    private final RuleBasedAdvisor rules = new RuleBasedAdvisor();
    private final AdviceValidator validator = new AdviceValidator();
    private CommerceContext context(int stock, int velocity, double peers, TriggerReason reason) {
        return new CommerceContext("P", "Product", Category.HOME, new BigDecimal("100.00"), stock, 10, velocity, peers, null, reason);
    }
    @Test void lowInventoryGetsTenPercentAndReplenishmentBuffer() {
        var advice = rules.advise(context(5, 1, 5, TriggerReason.INVENTORY_LOW), null);
        assertThat(advice.recommendedPrice()).isEqualByComparingTo("110.00");
        assertThat(advice.recommendedQuantity()).isEqualTo(25);
    }
    @Test void demandTwicePeerAverageGetsFivePercent() {
        var advice = rules.advise(context(30, 11, 5, TriggerReason.DEMAND_SPIKE), null);
        assertThat(advice.recommendedPrice()).isEqualByComparingTo("105.00");
        assertThat(advice.recommendedQuantity()).isEqualTo(1);
    }
    @Test void otherwiseHoldsAndLowInventoryWinsWhenBothConditionsApply() {
        assertThat(rules.advise(context(30, 10, 5, TriggerReason.MANUAL), null).direction()).isEqualTo(Direction.HOLD);
        assertThat(rules.advise(context(2, 50, 5, TriggerReason.DEMAND_SPIKE), null).recommendedPrice()).isEqualByComparingTo("110.00");
    }
    @Test void extremePricesAndNonFiniteConfidenceAreRejected() {
        var c = context(20, 1, 5, TriggerReason.MANUAL);
        assertThatThrownBy(() -> validator.validate(c, new CommerceAdvice(new BigDecimal("1000"), Direction.INCREASE, .8, "reason", 1, 7, .8, "reason"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(c, new CommerceAdvice(new BigDecimal("100"), Direction.HOLD, Double.NaN, "reason", 1, 7, .8, "reason"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(c, new CommerceAdvice(new BigDecimal("100"), Direction.INCREASE, .8, "reason", 1, 7, .8, "reason"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void aiFailureProducesBothRuleBasedRecommendations() {
        CommerceAdvisor ai = new CommerceAdvisor() {
            public StrategyType type() { return StrategyType.AI; }
            public CommerceAdvice advise(CommerceContext c, java.util.function.Consumer<String> tokens) { throw new IllegalStateException("Provider unavailable"); }
        };
        var settings = new CommerceSettings(StrategyType.AI, 3);
        var engine = new RecommendationEngine(List.of(rules, ai), settings, rules, validator);
        var result = engine.recommend(context(4, 2, 3, TriggerReason.INVENTORY_LOW), null);
        assertThat(result.source()).isEqualTo(RecommendationSource.RULE_BASED_FALLBACK);
        assertThat(result.advice().recommendedPrice()).isEqualByComparingTo("110");
        assertThat(result.advice().recommendedQuantity()).isEqualTo(26);
        settings.update(StrategyType.RULE_BASED, 4);
        assertThat(engine.recommend(context(20, 1, 3, TriggerReason.MANUAL), null).source()).isEqualTo(RecommendationSource.RULE_BASED);
    }
    @Test void malformedAndFractionalAiQuantitiesFallBack() {
        GroqClient client = mock(GroqClient.class);
        var ai = new AiCommerceAdvisor(client, new ObjectMapper());
        var engine = new RecommendationEngine(List.of(rules, ai), new CommerceSettings(StrategyType.AI, 3), rules, validator);
        when(client.complete(anyString(), anyString(), isNull())).thenReturn("not-json", "{\"recommendedPrice\":100,\"direction\":\"HOLD\",\"pricingConfidence\":0.8,\"pricingReasoning\":\"hold\",\"recommendedQuantity\":1.5,\"suggestedLeadTimeDays\":7,\"reorderConfidence\":0.8,\"reorderReasoning\":\"buffer\"}");
        assertThat(engine.recommend(context(5, 1, 3, TriggerReason.MANUAL), null).source()).isEqualTo(RecommendationSource.RULE_BASED_FALLBACK);
        assertThat(engine.recommend(context(5, 1, 3, TriggerReason.MANUAL), null).source()).isEqualTo(RecommendationSource.RULE_BASED_FALLBACK);
    }
    @Test void lowInventoryAndSpikeUseDifferentPromptInstructions() {
        GroqClient client = mock(GroqClient.class);
        when(client.complete(anyString(), anyString(), isNull())).thenReturn("{}");
        var advisor = new AiCommerceAdvisor(client, new ObjectMapper());
        try { advisor.advise(context(4, 1, 3, TriggerReason.INVENTORY_LOW), null); } catch (RuntimeException ignored) {}
        try { advisor.advise(context(40, 20, 3, TriggerReason.DEMAND_SPIKE), null); } catch (RuntimeException ignored) {}
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).complete(prompt.capture(), anyString(), isNull());
        assertThat(prompt.getAllValues().get(0)).contains("clearance").isNotEqualTo(prompt.getAllValues().get(1));
        assertThat(prompt.getAllValues().get(1)).contains("momentum");
    }
}
