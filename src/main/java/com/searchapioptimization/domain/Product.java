package com.searchapioptimization.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Long salesCount;

    @Column(nullable = false)
    private Boolean promoted;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Product(String name, String brand, String category,
                   Long price, Long salesCount, Boolean promoted, LocalDateTime createdAt) {
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.price = price;
        this.salesCount = salesCount != null ? salesCount : 0L;
        this.promoted = promoted != null ? promoted : false;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String brand, String category,
                       Long price, Long salesCount, Boolean promoted) {
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.price = price;
        this.salesCount = salesCount != null ? salesCount : this.salesCount;
        this.promoted = promoted != null ? promoted : this.promoted;
        this.updatedAt = LocalDateTime.now();
    }
}
