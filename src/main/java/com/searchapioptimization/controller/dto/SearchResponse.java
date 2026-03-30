package com.searchapioptimization.controller.dto;

import com.searchapioptimization.domain.Product;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record SearchResponse(
        List<ProductDto> products,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String searchType
) {

    @Builder
    public record ProductDto(
            Long id,
            String name,
            String brand,
            String category,
            Long price,
            Long salesCount,
            LocalDateTime createdAt
    ) {
        public static ProductDto from(Product product) {
            return ProductDto.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .brand(product.getBrand())
                    .category(product.getCategory())
                    .price(product.getPrice())
                    .salesCount(product.getSalesCount())
                    .createdAt(product.getCreatedAt())
                    .build();
        }
    }
}
