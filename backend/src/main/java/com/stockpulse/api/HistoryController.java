package com.stockpulse.api;
import com.stockpulse.api.dto.ProductHistoryResponse;
import com.stockpulse.service.HistoryService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/products/{id}/history")
public class HistoryController {
    private final HistoryService service;
    public HistoryController(HistoryService service) { this.service = service; }
    @GetMapping
    public ProductHistoryResponse history(@PathVariable String id) { return service.history(id); }
}
