package com.searchapioptimization.repository;

import com.searchapioptimization.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // ZeroOffset: ES 인덱싱용 (OFFSET 안티패턴 제거)
    @Query("SELECT p FROM Product p WHERE p.id > :lastId ORDER BY p.id ASC")
    List<Product> findByIdGreaterThan(@Param("lastId") Long lastId, Pageable pageable);

    // LIKE '%keyword%' — Full Table Scan (worst case)
    Page<Product> findByNameContaining(String keyword, Pageable pageable);

    // FULLTEXT — N-gram inverted index (Page: SELECT + COUNT 2회 실행)
    @Query(value = "SELECT * FROM product WHERE MATCH(name) AGAINST(:keyword IN BOOLEAN MODE)",
            countQuery = "SELECT COUNT(*) FROM product WHERE MATCH(name) AGAINST(:keyword IN BOOLEAN MODE)",
            nativeQuery = true)
    Page<Product> findByFulltext(@Param("keyword") String keyword, Pageable pageable);

    // FULLTEXT — Slice 반환 (COUNT 쿼리 제거, SELECT 1회만 실행)
    @Query(value = "SELECT * FROM product WHERE MATCH(name) AGAINST(:keyword IN BOOLEAN MODE)",
            nativeQuery = true)
    Slice<Product> findByFulltextSlice(@Param("keyword") String keyword, Pageable pageable);
}
