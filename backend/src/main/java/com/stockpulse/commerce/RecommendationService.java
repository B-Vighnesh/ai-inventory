package com.stockpulse.commerce;

import com.stockpulse.api.dto.RecommendationJobResponse;
import com.stockpulse.enums.*;
import com.stockpulse.service.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
public class RecommendationService {
    private static final int HISTORY_LIMIT = 1000;
    private final GenerationStore store;
    private final RecommendationEngine engine;
    private final TaskExecutor executor;
    private final Clock clock;
    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, Job> inFlight = new HashMap<>();
    private static class Job {
        volatile RecommendationJobResponse view;
        final CompletableFuture<RecommendationJobResponse> done = new CompletableFuture<>();
        final boolean forceRefresh;
        Job(RecommendationJobResponse view, boolean forceRefresh) { this.view = view; this.forceRefresh = forceRefresh; }
    }
    public RecommendationService(GenerationStore store, RecommendationEngine engine,
            @Qualifier("recommendationExecutor") TaskExecutor executor, Clock clock) {
        this.store = store; this.engine = engine; this.executor = executor; this.clock = clock;
    }
    public RecommendationJobResponse submit(String productId, TriggerReason trigger, SuggestionType type) {
        return submit(productId, trigger, type, null, null, false);
    }
    public RecommendationJobResponse submit(String productId, TriggerReason trigger, SuggestionType type, boolean forceRefresh) {
        return submit(productId, trigger, type, null, null, forceRefresh);
    }
    public synchronized RecommendationJobResponse submit(String productId, TriggerReason trigger, SuggestionType type,
            Consumer<String> tokens, Consumer<RecommendationJobResponse> completed) {
        return submit(productId, trigger, type, tokens, completed, false);
    }
    public synchronized RecommendationJobResponse submit(String productId, TriggerReason trigger, SuggestionType type,
            Consumer<String> tokens, Consumer<RecommendationJobResponse> completed, boolean forceRefresh) {
        store.context(productId, trigger); // Return 404 before admitting an invalid request.
        String key = productId + ":" + trigger + ":" + type;
        Job existing = inFlight.get(key);
        if (existing != null) {
            if (completed != null) existing.done.thenAccept(completed);
            return existing.view;
        }
        Instant now = clock.instant();
        Job job = new Job(new RecommendationJobResponse(UUID.randomUUID().toString(), productId, trigger, type,
            JobStatus.QUEUED, null, "Queued for review", null, null, now, now), forceRefresh);
        if (completed != null) job.done.thenAccept(completed);
        jobs.put(job.view.id(), job); inFlight.put(key, job);
        prune();
        try { executor.execute(() -> process(key, job, tokens)); }
        catch (RuntimeException full) {
            finish(job, JobStatus.FAILED, null, "Recommendation queue is full; retry shortly");
            inFlight.remove(key);
            job.done.complete(job.view);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Recommendation queue is full; retry shortly");
        }
        return job.view;
    }
    private void process(String key, Job job, Consumer<String> tokens) {
        RecommendationJobResponse input = job.view;
        try {
            finish(job, JobStatus.RUNNING, null, "Reviewing inventory and demand");
            var existing = job.forceRefresh ? Optional.<GenerationStore.Saved>empty()
                : store.existing(input.productId(), input.triggerReason(), input.type());
            GenerationStore.Saved saved;
            if (existing.isPresent()) saved = existing.get();
            else {
                CommerceContext context = store.context(input.productId(), input.triggerReason());
                saved = store.save(context, engine.recommend(context, tokens), input.type());
            }
            finish(job, JobStatus.COMPLETED, saved, saved.note());
        } catch (RuntimeException failure) {
            finish(job, JobStatus.FAILED, null, "Recommendation could not be saved; retry the review");
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Recommendation job {} failed ({})", input.id(), failure.getClass().getSimpleName());
        } finally {
            synchronized (this) { inFlight.remove(key, job); }
            job.done.complete(job.view);
        }
    }
    private void finish(Job job, JobStatus status, GenerationStore.Saved saved, String message) {
        var old = job.view;
        job.view = new RecommendationJobResponse(old.id(), old.productId(), old.triggerReason(), old.type(), status,
            saved == null ? null : saved.source(), message, saved == null ? null : saved.pricingId(),
            saved == null ? null : saved.reorderId(), old.createdAt(), clock.instant());
    }
    private void prune() {
        if (jobs.size() <= HISTORY_LIMIT) return;
        jobs.values().stream().filter(j -> j.done.isDone()).sorted(Comparator.comparing(j -> j.view.createdAt()))
            .limit(jobs.size() - HISTORY_LIMIT).forEach(j -> jobs.remove(j.view.id()));
    }
    public RecommendationJobResponse get(String id) {
        Job job = jobs.get(id);
        if (job == null) throw ApiException.notFound("Recommendation job", id);
        return job.view;
    }
    public List<RecommendationJobResponse> recent() {
        return jobs.values().stream().map(j -> j.view).sorted(Comparator.comparing(RecommendationJobResponse::createdAt).reversed()).limit(100).toList();
    }
}
