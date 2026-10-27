package com.stockpulse.api;
import com.stockpulse.api.dto.*;
import com.stockpulse.enums.SuggestionStatus;
import com.stockpulse.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class SuggestionController {
    private final SuggestionService service;
    public SuggestionController(SuggestionService service) { this.service = service; }
    @GetMapping("/pricing-suggestions")
    public List<PricingSuggestionResponse> pricing(@RequestParam(required = false) SuggestionStatus status, @RequestParam(required = false) String productId) { return service.pricing(status, productId); }
    @GetMapping("/reorder-suggestions")
    public List<ReorderSuggestionResponse> reorders(@RequestParam(required = false) SuggestionStatus status, @RequestParam(required = false) String productId) { return service.reorders(status, productId); }
    @PatchMapping("/pricing-suggestions/{id}")
    public PricingSuggestionResponse pricing(@PathVariable String id, @Valid @RequestBody ResolveRequest request) { return service.resolvePricing(id, request.action()); }
    @PatchMapping("/reorder-suggestions/{id}")
    public ReorderSuggestionResponse reorder(@PathVariable String id, @Valid @RequestBody ResolveRequest request) { return service.resolveReorder(id, request.action()); }
}
