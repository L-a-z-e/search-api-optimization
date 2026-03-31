package com.searchapioptimization.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.searchapioptimization.domain.ProductDocument;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutocompleteService {

    private final StringRedisTemplate redisTemplate;
    private final ElasticsearchOperations operations;
    private final JdbcTemplate jdbcTemplate;

    private static final String AUTOCOMPLETE_KEY = "autocomplete";

    // === Redis Sorted Set 방식 ===

    public List<String> suggestByRedis(String prefix, int limit) {
        Set<String> results = redisTemplate.opsForZSet()
                .reverseRangeByScore(AUTOCOMPLETE_KEY + ":" + prefix, 0, Double.MAX_VALUE, 0, limit);
        return results != null ? new ArrayList<>(results) : List.of();
    }

    public Map<String, Object> warmUpRedis() {
        long startTime = System.currentTimeMillis();

        // 인기 검색어 = 판매량 상위 브랜드+상품 조합에서 추출
        List<Map<String, Object>> topBrands = jdbcTemplate.queryForList(
                "SELECT brand, SUM(sales_count) as total FROM product GROUP BY brand ORDER BY total DESC LIMIT 100");

        List<Map<String, Object>> topProducts = jdbcTemplate.queryForList(
                "SELECT DISTINCT SUBSTRING_INDEX(name, ' ', 2) as term, SUM(sales_count) as total " +
                        "FROM product GROUP BY term ORDER BY total DESC LIMIT 1000");

        int count = 0;
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // 브랜드 자동완성
        for (var row : topBrands) {
            String brand = (String) row.get("brand");
            double score = ((Number) row.get("total")).doubleValue();
            addToAutocomplete(zSetOps, brand, score);
            count++;
        }

        // 상품명 접두사 자동완성
        for (var row : topProducts) {
            String term = (String) row.get("term");
            double score = ((Number) row.get("total")).doubleValue();
            addToAutocomplete(zSetOps, term, score);
            count++;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Redis 자동완성 warm-up 완료: {}건, {}ms", count, elapsed);

        return Map.of("warmedUp", count, "elapsedMs", elapsed);
    }

    private void addToAutocomplete(ZSetOperations<String, String> ops, String term, double score) {
        if (term == null || term.isBlank()) return;

        // 접두사별로 등록: "삼" → "삼성", "삼성" → "삼성 갤럭시", ...
        for (int i = 1; i <= term.length(); i++) {
            String prefix = term.substring(0, i);
            ops.add(AUTOCOMPLETE_KEY + ":" + prefix, term, score);
        }
    }

    // === ES match query 방식 (비교용, 느림) ===

    public List<String> suggestByEsMatch(String query, int limit) {
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m.field("name").query(query)))
                .withPageable(PageRequest.of(0, limit))
                .build();

        SearchHits<ProductDocument> hits = operations.search(searchQuery, ProductDocument.class);
        return hits.getSearchHits().stream()
                .map(h -> h.getContent().getName())
                .distinct()
                .toList();
    }
}
