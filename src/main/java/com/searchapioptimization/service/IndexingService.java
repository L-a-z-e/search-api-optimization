package com.searchapioptimization.service;

import com.searchapioptimization.domain.Product;
import com.searchapioptimization.domain.ProductDocument;
import com.searchapioptimization.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {

    private final ProductRepository productRepository;
    private final ElasticsearchOperations operations;

    private static final int BATCH_SIZE = 5000;

    public Map<String, Object> indexAll() {
        long startTime = System.currentTimeMillis();
        int totalIndexed = 0;
        int pageNum = 0;

        while (true) {
            Page<Product> page = productRepository.findAll(PageRequest.of(pageNum, BATCH_SIZE));
            if (page.isEmpty()) break;

            List<IndexQuery> queries = page.getContent().stream()
                    .map(p -> new IndexQueryBuilder()
                            .withId(String.valueOf(p.getId()))
                            .withObject(ProductDocument.from(p))
                            .build()
                    )
                    .toList();

            operations.bulkIndex(queries, operations.getIndexCoordinatesFor(ProductDocument.class));
            totalIndexed += queries.size();

            if (totalIndexed % 50000 == 0) {
                log.info("ES 인덱싱 진행: {}건", totalIndexed);
            }

            pageNum++;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("ES 인덱싱 완료 (OFFSET): {}건, {}초", totalIndexed, elapsed / 1000);

        return Map.of(
                "totalIndexed", totalIndexed,
                "elapsedSeconds", elapsed / 1000,
                "method", "OFFSET"
        );
    }

    public Map<String, Object> indexAllZeroOffset() {
        long startTime = System.currentTimeMillis();
        int totalIndexed = 0;
        Long lastId = 0L;

        while (true) {
            List<Product> products = productRepository.findByIdGreaterThan(lastId, PageRequest.of(0, BATCH_SIZE));
            if (products.isEmpty()) break;

            List<IndexQuery> queries = products.stream()
                    .map(p -> new IndexQueryBuilder()
                            .withId(String.valueOf(p.getId()))
                            .withObject(ProductDocument.from(p))
                            .build()
                    )
                    .toList();

            operations.bulkIndex(queries, operations.getIndexCoordinatesFor(ProductDocument.class));
            totalIndexed += queries.size();
            lastId = products.get(products.size() - 1).getId();

            if (totalIndexed % 50000 == 0) {
                log.info("ES 인덱싱 진행 (ZeroOffset): {}건", totalIndexed);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("ES 인덱싱 완료 (ZeroOffset): {}건, {}초", totalIndexed, elapsed / 1000);

        return Map.of(
                "totalIndexed", totalIndexed,
                "elapsedSeconds", elapsed / 1000,
                "method", "ZeroOffset"
        );
    }
}
