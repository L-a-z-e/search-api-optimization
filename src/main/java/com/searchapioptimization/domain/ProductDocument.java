package com.searchapioptimization.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Document(indexName = "products", createIndex = false)
public class ProductDocument {

    @Id
    private Long id;

    private String name;
    private String brand;
    private String category;
    private Long price;
    private Long salesCount;
    private Boolean promoted;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;

    @Builder
    public ProductDocument(Long id, String name, String brand, String category,
                           Long price, Long salesCount, Boolean promoted, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.price = price;
        this.salesCount = salesCount;
        this.promoted = promoted;
        this.createdAt = createdAt;
    }

    public static ProductDocument from(Product product) {
        return ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory())
                .price(product.getPrice())
                .salesCount(product.getSalesCount())
                .promoted(product.getPromoted())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
