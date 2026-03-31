package com.searchapioptimization.controller;

import com.searchapioptimization.domain.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cdc")
@RequiredArgsConstructor
public class CdcVerifyController {

    private final JdbcTemplate jdbcTemplate;
    private final ElasticsearchOperations operations;

    @GetMapping("/verify/{id}")
    public Map<String, Object> verify(@PathVariable Long id) {
        // MySQL에서 조회
        var mysqlResult = jdbcTemplate.queryForMap(
                "SELECT id, name, brand, price FROM product WHERE id = ?", id);

        // ES에서 조회
        ProductDocument esResult = operations.get(String.valueOf(id), ProductDocument.class);

        boolean synced = esResult != null
                && mysqlResult.get("name").equals(esResult.getName())
                && mysqlResult.get("brand").equals(esResult.getBrand());

        return Map.of(
                "mysqlId", mysqlResult.get("id"),
                "mysqlName", mysqlResult.get("name"),
                "esFound", esResult != null,
                "esName", esResult != null ? esResult.getName() : "NOT_FOUND",
                "synced", synced
        );
    }

    @GetMapping("/lag-test")
    public Map<String, Object> lagTest() throws InterruptedException {
        // 1. MySQL에 테스트 상품 INSERT
        long startTime = System.currentTimeMillis();
        String testName = "CDC_LAG_TEST_" + System.currentTimeMillis();

        jdbcTemplate.update(
                "INSERT INTO product (name, brand, category, price, sales_count, promoted, created_at, updated_at) " +
                        "VALUES (?, 'TEST', 'TEST', 1000, 0, false, NOW(), NOW())",
                testName);

        Long insertedId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ?", Long.class, testName);

        // 2. ES에 반영될 때까지 폴링
        long maxWaitMs = 30000;
        long pollInterval = 500;
        boolean found = false;
        long lagMs = 0;

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            Thread.sleep(pollInterval);
            ProductDocument doc = operations.get(String.valueOf(insertedId), ProductDocument.class);
            if (doc != null && testName.equals(doc.getName())) {
                lagMs = System.currentTimeMillis() - startTime;
                found = true;
                break;
            }
        }

        // 3. 테스트 상품 삭제
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", insertedId);

        return Map.of(
                "testName", testName,
                "insertedId", insertedId,
                "syncedToEs", found,
                "lagMs", found ? lagMs : -1,
                "lagSeconds", found ? lagMs / 1000.0 : -1
        );
    }

    @GetMapping("/delete-test/{id}")
    public Map<String, Object> deleteTest(@PathVariable Long id) throws InterruptedException {
        // 1. MySQL에서 삭제
        long startTime = System.currentTimeMillis();
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", id);

        // 2. ES에서 삭제될 때까지 폴링
        long maxWaitMs = 30000;
        long pollInterval = 500;
        boolean deleted = false;
        long lagMs = 0;

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            Thread.sleep(pollInterval);
            ProductDocument doc = operations.get(String.valueOf(id), ProductDocument.class);
            if (doc == null) {
                lagMs = System.currentTimeMillis() - startTime;
                deleted = true;
                break;
            }
        }

        return Map.of(
                "deletedId", id,
                "deletedFromEs", deleted,
                "lagMs", deleted ? lagMs : -1,
                "lagSeconds", deleted ? lagMs / 1000.0 : -1
        );
    }
}
