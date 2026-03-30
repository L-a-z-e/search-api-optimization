package com.searchapioptimization.repository;

import com.searchapioptimization.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // LIKE '%keyword%' — Full Table Scan (worst case)
    Page<Product> findByNameContaining(String keyword, Pageable pageable);

    // FULLTEXT — N-gram inverted index (better, but limited)
    @Query(value = "SELECT * FROM product WHERE MATCH(name) AGAINST(:keyword IN BOOLEAN MODE)",
            countQuery = "SELECT COUNT(*) FROM product WHERE MATCH(name) AGAINST(:keyword IN BOOLEAN MODE)",
            nativeQuery = true)
    Page<Product> findByFulltext(@Param("keyword") String keyword, Pageable pageable);
}
