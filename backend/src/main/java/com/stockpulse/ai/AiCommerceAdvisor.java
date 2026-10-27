package com.stockpulse.ai;

import com.fasterxml.jackson.databind.*;
import com.stockpulse.commerce.*;
import com.stockpulse.enums.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
public class AiCommerceAdvisor implements CommerceAdvisor {
    private final GroqClient client;
    private final ObjectMapper json;
    public AiCommerceAdvisor(GroqClient client, ObjectMapper json) { this.client = client; this.json = json; }
    @Override public StrategyType type() { return StrategyType.AI; }
    @Override public CommerceAdvice advise(CommerceContext context, Consumer<String> tokens) {
        String file = switch (context.triggerReason()) {
            case INVENTORY_LOW -> "inventory-low.txt";
            case DEMAND_SPIKE -> "demand-spike.txt";
            default -> "manual.txt";
        };
        try {
            String prompt = new ClassPathResource("prompts/" + file).getContentAsString(StandardCharsets.UTF_8);
            String schema = new ClassPathResource("prompts/output-contract.txt").getContentAsString(StandardCharsets.UTF_8);
            String response = client.complete(prompt + "\n" + schema, json.writeValueAsString(context), tokens);
            JsonNode node = json.readTree(response);
            String[] required = {"recommendedPrice", "direction", "pricingConfidence", "pricingReasoning", "recommendedQuantity", "suggestedLeadTimeDays", "reorderConfidence", "reorderReasoning"};
            for (String field : required) if (!node.hasNonNull(field)) throw new IllegalArgumentException("Missing recommendation field");
            if (!node.get("recommendedPrice").isNumber() || !node.get("recommendedQuantity").isIntegralNumber()
                    || !node.get("recommendedQuantity").canConvertToInt() || !node.get("suggestedLeadTimeDays").isIntegralNumber()
                    || !node.get("suggestedLeadTimeDays").canConvertToInt()
                    || !node.get("pricingConfidence").isNumber() || !node.get("reorderConfidence").isNumber()
                    || !node.get("pricingReasoning").isTextual() || !node.get("reorderReasoning").isTextual())
                throw new IllegalArgumentException("Invalid recommendation types");
            return new CommerceAdvice(node.get("recommendedPrice").decimalValue(), Direction.valueOf(node.get("direction").asText()),
                node.get("pricingConfidence").doubleValue(), node.get("pricingReasoning").asText(),
                node.get("recommendedQuantity").intValue(), node.get("suggestedLeadTimeDays").intValue(),
                node.get("reorderConfidence").doubleValue(), node.get("reorderReasoning").asText());
        } catch (IOException failure) { throw new IllegalStateException("Unable to parse recommendation", failure); }
    }
}
