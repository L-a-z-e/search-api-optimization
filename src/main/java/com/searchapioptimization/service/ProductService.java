package com.searchapioptimization.service;

import com.searchapioptimization.controller.dto.ProductCreateRequest;
import com.searchapioptimization.domain.Product;
import com.searchapioptimization.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public Product create(ProductCreateRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .brand(request.brand())
                .category(request.category())
                .price(request.price())
                .salesCount(request.salesCount())
                .promoted(request.promoted())
                .build();
        return productRepository.save(product);
    }

    @Transactional
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional
    public Product update(Long id, ProductCreateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        product.update(request.name(), request.brand(), request.category(),
                request.price(), request.salesCount(), request.promoted());
        return product;
    }
}
