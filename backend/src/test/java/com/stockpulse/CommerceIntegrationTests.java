package com.stockpulse;

import com.stockpulse.api.dto.RecommendationJobResponse;
import com.stockpulse.commerce.*;
import com.stockpulse.domain.*;
import com.stockpulse.enums.*;
import com.stockpulse.repository.*;
import com.stockpulse.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "spring.datasource.url=jdbc:h2:mem:commerce-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "stockpulse.commerce.strategy=RULE_BASED", "stockpulse.commerce.initial-generation=false", "stockpulse.groq.api-key="})
@AutoConfigureMockMvc
class CommerceIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductRepository products;
    @Autowired PricingSuggestionRepository pricing;
    @Autowired ReorderSuggestionRepository reorders;
    @Autowired SaleRecordRepository sales;
    @Autowired DemandService demand;
    @Autowired ProductService productService;
    @Autowired RecommendationService jobs;
    @Autowired CommerceSettings settings;
    @Autowired GenerationStore store;
    @Autowired RecommendationEngine engine;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void rules() { settings.update(StrategyType.RULE_BASED, 3); }
    private Product product(int stock, int threshold) {
        Product p = new Product(); p.setId("TEST-" + UUID.randomUUID()); p.setSku(p.getId()); p.setName("Integration product");
        p.setCategory(Category.ELECTRONICS); p.setStockLevel(stock); p.setReorderThreshold(threshold);
        p.setCurrentPrice(new BigDecimal("100.00")); p.setStatus(stock == 0 ? ProductStatus.OUT_OF_STOCK : ProductStatus.ACTIVE);
        return products.saveAndFlush(p);
    }
    private RecommendationJobResponse await(String id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var job = jobs.get(id);
            if (job.status() == JobStatus.COMPLETED || job.status() == JobStatus.FAILED) {
                assertThat(job.status()).as(job.message()).isEqualTo(JobStatus.COMPLETED); return job;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Job did not complete");
    }
    private RecommendationJobResponse triggerJob(String id, TriggerReason reason) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var match = jobs.recent().stream().filter(j -> j.productId().equals(id) && j.triggerReason() == reason).findFirst();
            if (match.isPresent()) return await(match.get().id());
            Thread.sleep(20);
        }
        throw new AssertionError("No triggered job");
    }
    @Test void stockChangeAutomaticallyGeneratesBothAndDeduplicatesRepeatedTriggers() throws Exception {
        Product p = product(20, 10);
        mvc.perform(patch("/products/" + p.getId() + "/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockLevel\":5}"))
            .andExpect(status().isOk());
        var first = triggerJob(p.getId(), TriggerReason.INVENTORY_LOW);
        assertThat(first.pricingId()).isNotNull(); assertThat(first.reorderId()).isNotNull();
        assertThat(products.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.PRICE_REVIEW_PENDING);
        var repeat = await(jobs.submit(p.getId(), TriggerReason.INVENTORY_LOW, SuggestionType.BOTH).id());
        assertThat(repeat.pricingId()).isEqualTo(first.pricingId()); assertThat(repeat.reorderId()).isEqualTo(first.reorderId());
    }
    @Test void demandSpikeTriggersWithoutLowStock() throws Exception {
        Product p = product(100, 0);
        mvc.perform(post("/products/" + p.getId() + "/orders").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":20}"))
            .andExpect(status().isOk());
        var job = triggerJob(p.getId(), TriggerReason.DEMAND_SPIKE);
        assertThat(job.pricingId()).isNotNull(); assertThat(job.reorderId()).isNotNull();
    }
    @Test void manualEndpointsAndRuntimeSwitchingWorkWithFallback() throws Exception {
        Product p = product(20, 10);
        mvc.perform(patch("/settings/commerce").contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"AI\",\"demandSpikeMultiplier\":4}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.strategy").value("AI"));
        String body = mvc.perform(post("/products/" + p.getId() + "/suggest-pricing")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        var priceJob = await(json.readTree(body).get("id").asText());
        assertThat(priceJob.source()).isEqualTo(RecommendationSource.RULE_BASED_FALLBACK);
        assertThat(priceJob.pricingId()).isNotNull(); assertThat(priceJob.reorderId()).isNull();
        settings.update(StrategyType.RULE_BASED, 3);
        body = mvc.perform(post("/products/" + p.getId() + "/suggest-reorder")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(await(json.readTree(body).get("id").asText()).reorderId()).isNotNull();
    }
    @Test void eachSequentialManualReviewGeneratesFreshRecommendations() throws Exception {
        Product p = product(20, 10);
        String firstBody = mvc.perform(post("/products/" + p.getId() + "/review")).andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        var first = await(json.readTree(firstBody).get("id").asText());

        String secondBody = mvc.perform(post("/products/" + p.getId() + "/review")).andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        var second = await(json.readTree(secondBody).get("id").asText());

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.pricingId()).isNotEqualTo(first.pricingId());
        assertThat(second.reorderId()).isNotEqualTo(first.reorderId());
        assertThat(pricing.findById(first.pricingId()).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.SUPERSEDED);
        assertThat(reorders.findById(first.reorderId()).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.SUPERSEDED);
    }
    @Test void oldSalesExpireFromDemandAndFutureSalesAreExcluded() throws Exception {
        Product p = product(100, 0);
        sales.saveAndFlush(new SaleRecord(p.getId(), 9, Instant.now().minus(Duration.ofHours(25))));
        sales.saveAndFlush(new SaleRecord(p.getId(), 4, Instant.now().minus(Duration.ofHours(1))));
        sales.saveAndFlush(new SaleRecord(p.getId(), 7, Instant.now().plus(Duration.ofHours(1))));
        assertThat(demand.velocity(p.getId())).isEqualTo(4);
        mvc.perform(get("/products/" + p.getId())).andExpect(jsonPath("$.demandVelocity").value(4));
    }
    @Test void rolledBackOrderDoesNotGenerateRecommendationsOrHistory() throws Exception {
        Product p = product(5, 10);
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> { productService.order(p.getId(), 1); throw new IllegalStateException("rollback"); });
        } catch (IllegalStateException ignored) {}
        Thread.sleep(150);
        assertThat(jobs.recent()).noneMatch(j -> j.productId().equals(p.getId()));
        assertThat(demand.velocity(p.getId())).isZero();
        assertThat(products.findById(p.getId()).orElseThrow().getStockLevel()).isEqualTo(5);
    }
    @Test void acceptingRecommendationRecordsHistoryAndRequiresHumanAction() throws Exception {
        Product p = product(5, 10);
        var job = await(jobs.submit(p.getId(), TriggerReason.MANUAL, SuggestionType.BOTH).id());
        assertThat(products.findById(p.getId()).orElseThrow().getCurrentPrice()).isEqualByComparingTo("100");
        mvc.perform(patch("/pricing-suggestions/" + job.pricingId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk());
        mvc.perform(patch("/reorder-suggestions/" + job.reorderId()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/products/" + p.getId() + "/history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.prices[0].newPrice").value(110)).andExpect(jsonPath("$.inventory[0].stockLevel").value(30));
    }
    @Test void staleAiContextIsRecalculatedBeforeSaving() {
        Product p = product(20, 10);
        var context = store.context(p.getId(), TriggerReason.MANUAL);
        var result = engine.recommend(context, null);
        p.setStockLevel(2); products.saveAndFlush(p);
        var saved = store.save(context, result, SuggestionType.BOTH);
        assertThat(saved.source()).isEqualTo(RecommendationSource.RULE_BASED_FALLBACK);
        assertThat(pricing.findById(saved.pricingId()).orElseThrow().getRecommendedPrice()).isEqualByComparingTo("110");
    }
    @Test void newGenerationSupersedesOlderPendingSuggestionsOfBothTypes() {
        Product p = product(20, 10);
        var old = store.save(store.context(p.getId(), TriggerReason.MANUAL),
            engine.recommend(store.context(p.getId(), TriggerReason.MANUAL), null), SuggestionType.BOTH);

        var context = store.context(p.getId(), TriggerReason.DEMAND_SPIKE);
        var fresh = store.save(context, engine.recommend(context, null), SuggestionType.BOTH);

        assertThat(pricing.findById(old.pricingId()).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.SUPERSEDED);
        assertThat(reorders.findById(old.reorderId()).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.SUPERSEDED);
        assertThat(pricing.findById(fresh.pricingId()).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(reorders.findById(fresh.reorderId()).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(pricing.findAll().stream().filter(s -> s.getProduct().getId().equals(p.getId()) && s.getStatus() == SuggestionStatus.PENDING)).hasSize(1);
        assertThat(reorders.findAll().stream().filter(s -> s.getProduct().getId().equals(p.getId()) && s.getStatus() == SuggestionStatus.PENDING)).hasSize(1);
    }
    @Test void concurrentRequestsCannotDuplicatePendingRecommendations() throws Exception {
        Product p = product(4, 10);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> jobs.submit(p.getId(), TriggerReason.MANUAL, SuggestionType.BOTH).id()));
            Set<String> priceIds = new HashSet<>();
            for (var future : futures) priceIds.add(await(future.get()).pricingId());
            assertThat(priceIds).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void streamingEndpointReturnsFinalPersistedSuggestion() throws Exception {
        Product p = product(8, 10);
        var request = mvc.perform(post("/products/" + p.getId() + "/suggest-pricing/stream")).andExpect(request().asyncStarted()).andReturn();
        request.getAsyncResult(10000);
        mvc.perform(asyncDispatch(request)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("event:result")));
    }
}
