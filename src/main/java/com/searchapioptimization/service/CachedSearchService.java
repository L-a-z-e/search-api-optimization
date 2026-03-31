package com.searchapioptimization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchapioptimization.controller.dto.SearchResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CachedSearchService {

    private final ElasticsearchService elasticsearchService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private static final String CACHE_PREFIX = "search:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private Counter cacheHitCounter;
    private Counter cacheMissCounter;

    @PostConstruct
    void initMetrics() {
        cacheHitCounter = Counter.builder("search.cache.hit").register(meterRegistry);
        cacheMissCounter = Counter.builder("search.cache.miss").register(meterRegistry);
    }

    public SearchResponse search(String query, int page, int size) {
        String cacheKey = buildCacheKey(query, page, size);

        // 1. 캐시 확인
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            cacheHitCounter.increment();
            try {
                return objectMapper.readValue(cached, SearchResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("캐시 역직렬화 실패, ES로 fallback: {}", cacheKey);
            }
        }

        // 2. 캐시 미스 → ES 검색
        cacheMissCounter.increment();
        SearchResponse result = elasticsearchService.search(query, page, size);

        // 3. 결과를 캐시에 저장
        try {
            String json = objectMapper.writeValueAsString(
                    SearchResponse.builder()
                            .products(result.products())
                            .page(result.page())
                            .size(result.size())
                            .totalElements(result.totalElements())
                            .totalPages(result.totalPages())
                            .searchType("CACHED_ES")
                            .build()
            );
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("캐시 직렬화 실패: {}", cacheKey);
        }

        return result;
    }

    public CacheStats getStats() {
        double hits = cacheHitCounter.count();
        double misses = cacheMissCounter.count();
        double total = hits + misses;
        double hitRate = total > 0 ? hits / total * 100 : 0;
        return new CacheStats(hits, misses, hitRate);
    }

    public void evictAll() {
        var keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("캐시 전체 삭제: {}건", keys.size());
        }
    }

    private String buildCacheKey(String query, int page, int size) {
        return CACHE_PREFIX + query + ":" + page + ":" + size;
    }

    public record CacheStats(double hits, double misses, double hitRatePercent) {}
}
