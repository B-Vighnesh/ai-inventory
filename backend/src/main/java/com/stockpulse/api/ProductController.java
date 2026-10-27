package com.stockpulse.api;
import com.stockpulse.enums.Category;
import com.stockpulse.enums.ProductStatus;
import com.stockpulse.api.dto.*;
import com.stockpulse.domain.*;
import com.stockpulse.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.List;
@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = service.create(request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/products/{id}").buildAndExpand(product.id()).encode().toUri()).body(product);
    }
    @GetMapping
    public List<ProductResponse> list(@RequestParam(required = false) ProductStatus status, @RequestParam(required = false) Category category) { return service.list(status, category); }
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable String id) { return service.get(id); }
    @PatchMapping("/{id}/stock")
    public ProductResponse stock(@PathVariable String id, @Valid @RequestBody StockRequest request) { return service.setStock(id, request.stockLevel()); }
    @PostMapping("/{id}/orders")
    public ProductResponse order(@PathVariable String id, @Valid @RequestBody OrderRequest request) { return service.order(id, request.effectiveQuantity()); }
}
