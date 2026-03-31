package com.searchapioptimization.controller;

import com.searchapioptimization.controller.dto.ProductCreateRequest;
import com.searchapioptimization.domain.Product;
import com.searchapioptimization.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody ProductCreateRequest request) {
        Product product = productService.create(request);
        return Map.of("id", product.getId(), "name", product.getName());
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody ProductCreateRequest request) {
        Product product = productService.update(id, request);
        return Map.of("id", product.getId(), "name", product.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
