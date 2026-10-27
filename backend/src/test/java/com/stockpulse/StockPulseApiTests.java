package com.stockpulse;
import com.stockpulse.enums.Category;
import com.stockpulse.enums.Direction;
import com.stockpulse.enums.ProductStatus;

import com.stockpulse.domain.*;
import com.stockpulse.events.StockChangedEvent;
import com.stockpulse.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:stockpulse-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class StockPulseApiTests {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired PricingSuggestionRepository pricing;
    @Autowired ReorderSuggestionRepository reorders;
    @Autowired ApplicationEvents events;

    @Test
    void orderDecrementsStockAndIncrementsVelocityAndPublishesEvent() throws Exception {
        mvc.perform(post("/products/PRD-001/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stockLevel").value(42))
            .andExpect(jsonPath("$.demandVelocity").value(6));
        assertThat(products.findById("PRD-001").orElseThrow().getStockLevel()).isEqualTo(42);
        assertThat(events.stream(StockChangedEvent.class)).containsExactly(new StockChangedEvent("PRD-001", 42, 6));
    }

    @Test
    void oversizedOrderReturnsConflictAndChangesNothing() throws Exception {
        mvc.perform(post("/products/PRD-001/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":46}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        Product p = products.findById("PRD-001").orElseThrow();
        assertThat(p.getStockLevel()).isEqualTo(45);
        assertThat(p.getDemandVelocity()).isEqualTo(3);
        assertThat(events.stream(StockChangedEvent.class)).isEmpty();
    }

    @Test
    void defaultOrderQuantityIsOne() throws Exception {
        mvc.perform(post("/products/PRD-001/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stockLevel").value(44))
            .andExpect(jsonPath("$.demandVelocity").value(4));
    }

    @Test
    void consumingLastStockMarksOutOfStock() throws Exception {
        mvc.perform(post("/products/PRD-003/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":8}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stockLevel").value(0))
            .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));
    }

    @Test
    void stockPatchUpdatesStatusAndPublishesEvents() throws Exception {
        mvc.perform(patch("/products/PRD-001/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));
        mvc.perform(patch("/products/PRD-001/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":5}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.demandVelocity").value(3));
        assertThat(events.stream(StockChangedEvent.class)).containsExactly(
            new StockChangedEvent("PRD-001", 0, 3), new StockChangedEvent("PRD-001", 5, 3));
    }

    @Test
    void positiveStockPreservesPriceReview() throws Exception {
        mvc.perform(patch("/products/PRD-003/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":10}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRICE_REVIEW_PENDING"));
    }

    @Test
    void acceptingPricingUpdatesPriceAndClearsReview() throws Exception {
        PricingSuggestion s = priceSuggestion("PRD-003");
        mvc.perform(patch("/pricing-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        Product p = products.findById("PRD-003").orElseThrow();
        assertThat(p.getCurrentPrice()).isEqualByComparingTo("29.99");
        assertThat(p.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void alreadyResolvedPricingReturnsConflict() throws Exception {
        PricingSuggestion s = priceSuggestion("PRD-001");
        mvc.perform(patch("/pricing-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk());
        mvc.perform(patch("/pricing-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void otherPendingPricingKeepsReviewStatus() throws Exception {
        PricingSuggestion first = priceSuggestion("PRD-003");
        PricingSuggestion second = priceSuggestion("PRD-003");
        mvc.perform(patch("/pricing-suggestions/" + first.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk());
        assertThat(products.findById("PRD-003").orElseThrow().getStatus()).isEqualTo(ProductStatus.PRICE_REVIEW_PENDING);
        mvc.perform(patch("/pricing-suggestions/" + second.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\"}"))
            .andExpect(status().isOk());
        assertThat(products.findById("PRD-003").orElseThrow().getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void rejectingPricingLeavesPriceUnchangedAndClearsReview() throws Exception {
        PricingSuggestion s = priceSuggestion("PRD-003");
        mvc.perform(patch("/pricing-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        Product p = products.findById("PRD-003").orElseThrow();
        assertThat(p.getCurrentPrice()).isEqualByComparingTo("24.99");
        assertThat(p.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void pricingAcceptancePreservesOutOfStockStatus() throws Exception {
        PricingSuggestion s = priceSuggestion("PRD-006");
        mvc.perform(patch("/pricing-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk());
        assertThat(products.findById("PRD-006").orElseThrow().getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void reorderAcceptanceAddsStockAndActivatesProduct() throws Exception {
        ReorderSuggestion s = reorderSuggestion("PRD-006");
        mvc.perform(patch("/reorder-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        Product p = products.findById("PRD-006").orElseThrow();
        assertThat(p.getStockLevel()).isEqualTo(25);
        assertThat(p.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void rejectingReorderPreservesStockAndClearsStaleReview() throws Exception {
        ReorderSuggestion s = reorderSuggestion("PRD-003");
        mvc.perform(patch("/reorder-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        Product p = products.findById("PRD-003").orElseThrow();
        assertThat(p.getStockLevel()).isEqualTo(8);
        assertThat(p.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void alreadyResolvedReorderReturnsConflict() throws Exception {
        ReorderSuggestion s = reorderSuggestion("PRD-006");
        mvc.perform(patch("/reorder-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk());
        mvc.perform(patch("/reorder-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void listsSeedProductsAndCombinesFilters() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(8)));
        mvc.perform(get("/products").param("status", "ACTIVE").param("category", "ELECTRONICS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/products/PRD-001")).andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("SKU-ELEC-001"));
    }

    @Test
    void filtersSuggestionsByStatusAndProduct() throws Exception {
        PricingSuggestion p = priceSuggestion("PRD-001"); priceSuggestion("PRD-003");
        ReorderSuggestion r = reorderSuggestion("PRD-006"); reorderSuggestion("PRD-003");
        mvc.perform(get("/pricing-suggestions").param("status", "PENDING").param("productId", "PRD-001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(p.getId()));
        mvc.perform(get("/reorder-suggestions").param("status", "PENDING").param("productId", "PRD-006"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(r.getId()));
    }

    @Test
    void createsProductAndRejectsDuplicateId() throws Exception {
        String body = "{\"id\":\"PRD-009\",\"sku\":\"SKU-NEW\",\"name\":\"New product\",\"category\":\"HOME\",\"currentPrice\":9.99,\"stockLevel\":0,\"reorderThreshold\":5}";
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/products/PRD-009"))
            .andExpect(jsonPath("$.status").value("OUT_OF_STOCK")).andExpect(jsonPath("$.demandVelocity").value(0));
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }

    @Test
    void duplicateSkuReturnsConflict() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(
            "{\"id\":\"PRD-009\",\"sku\":\"SKU-ELEC-001\",\"name\":\"Duplicate\",\"category\":\"HOME\",\"currentPrice\":10,\"stockLevel\":1,\"reorderThreshold\":0}"))
            .andExpect(status().isConflict());
    }

    @Test
    void invalidInputsReturnJsonBadRequest() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").isArray());
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(
            "{\"id\":\"NEW\",\"sku\":\"NEW\",\"name\":\"Invalid\",\"category\":\"HOME\",\"currentPrice\":0,\"stockLevel\":-1,\"reorderThreshold\":-1}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/products/PRD-001/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":-1}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/products/PRD-001/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/pricing-suggestions/missing").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"BOGUS\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/products").param("category", "BOGUS")).andExpect(status().isBadRequest());
    }

    @Test
    void missingResourcesReturnJsonNotFound() throws Exception {
        mvc.perform(get("/products/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(patch("/pricing-suggestions/missing").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/reorder-suggestions/missing").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsBothFrontendOrigins() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://localhost:4200"}) {
            mvc.perform(options("/products/PRD-001/stock").header("Origin", origin).header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentOrdersCannotOversell() throws Exception {
        Product p = concurrentProduct("RACE-ORDER");
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> order = () -> {
                start.await();
                return mvc.perform(post("/products/" + p.getId() + "/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(order); var second = executor.submit(order); start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
            Product result = products.findById(p.getId()).orElseThrow();
            assertThat(result.getStockLevel()).isZero();
            assertThat(result.getDemandVelocity()).isEqualTo(1);
        } finally {
            executor.shutdownNow(); products.deleteById(p.getId());
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentResolutionAppliesReorderOnlyOnce() throws Exception {
        Product p = concurrentProduct("RACE-RESOLVE");
        ReorderSuggestion s = reorderSuggestion(p.getId());
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> resolve = () -> {
                start.await();
                return mvc.perform(patch("/reorder-suggestions/" + s.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"action\":\"ACCEPT\"}")).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(resolve); var second = executor.submit(resolve); start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
            assertThat(products.findById(p.getId()).orElseThrow().getStockLevel()).isEqualTo(26);
        } finally {
            executor.shutdownNow(); reorders.deleteById(s.getId()); products.deleteById(p.getId());
        }
    }

    private Product concurrentProduct(String id) {
        Product p = new Product(); p.setId(id); p.setSku(id); p.setName("Concurrency fixture");
        p.setCategory(Category.HOME); p.setCurrentPrice(new BigDecimal("10.00"));
        p.setStockLevel(1); p.setStatus(ProductStatus.ACTIVE);
        return products.saveAndFlush(p);
    }

    private PricingSuggestion priceSuggestion(String productId) {
        Product p = products.findById(productId).orElseThrow();
        PricingSuggestion s = new PricingSuggestion(); s.setProduct(p); s.setCurrentPrice(p.getCurrentPrice());
        s.setRecommendedPrice(new BigDecimal("29.99")); s.setDirection(Direction.INCREASE);
        s.setConfidence(0.85); s.setReasoning("Test pricing fixture");
        return pricing.saveAndFlush(s);
    }
    private ReorderSuggestion reorderSuggestion(String productId) {
        Product p = products.findById(productId).orElseThrow();
        ReorderSuggestion s = new ReorderSuggestion(); s.setProduct(p); s.setCurrentStock(p.getStockLevel());
        s.setRecommendedQuantity(25); s.setSuggestedLeadTimeDays(3); s.setConfidence(0.9); s.setReasoning("Test reorder fixture");
        return reorders.saveAndFlush(s);
    }
}
