package com.searchapioptimization.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.searchapioptimization.controller.dto.SearchResponse;
import com.searchapioptimization.service.ElasticsearchService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiLayerCacheService {

    private final ElasticsearchService elasticsearchService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CacheEvictionPublisher evictionPublisher;

    private static final String CACHE_PREFIX = "search:";
    private static final Duration L2_TTL = Duration.ofMinutes(5);
    private static final long L1_TTL_SECONDS = 60;
    private static final long L1_MAX_SIZE = 10_000;

    // L1: Caffeine (JVM 로컬, 60초 TTL, 10,000 엔트리)
    private final Cache<String, SearchResponse> l1Cache = Caffeine.newBuilder()
            .expireAfterWrite(L1_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(L1_MAX_SIZE)
            .recordStats()
            .build();

    private Counter l1HitCounter;
    private Counter l2HitCounter;
    private Counter cacheMissCounter;

    @PostConstruct
    void initMetrics() {
        l1HitCounter = Counter.builder("search.cache.l1.hit").register(meterRegistry);
        l2HitCounter = Counter.builder("search.cache.l2.hit").register(meterRegistry);
        cacheMissCounter = Counter.builder("search.cache.miss").register(meterRegistry);
    }

    public SearchResponse search(String query, int page, int size) {
        String cacheKey = buildCacheKey(query, page, size);

        // 1. L1 (Caffeine) 확인
        SearchResponse l1Result = l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            l1HitCounter.increment();
            return l1Result;
        }

        // 2. L2 (Redis) 확인
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            l2HitCounter.increment();
            try {
                SearchResponse result = objectMapper.readValue(cached, SearchResponse.class);
                l1Cache.put(cacheKey, result); // L1에 승격
                return result;
            } catch (JsonProcessingException e) {
                log.warn("L2 캐시 역직렬화 실패, ES로 fallback: {}", cacheKey);
            }
        }

        // 3. 캐시 미스 → ES 검색
        cacheMissCounter.increment();
        SearchResponse result = elasticsearchService.search(query, page, size);

        SearchResponse cachedResult = SearchResponse.builder()
                .products(result.products())
                .page(result.page())
                .size(result.size())
                .totalElements(result.totalElements())
                .totalPages(result.totalPages())
                .searchType("MULTILAYER_CACHED")
                .build();

        // 4. L2 (Redis) 저장
        try {
            String json = objectMapper.writeValueAsString(cachedResult);
            redisTemplate.opsForValue().set(cacheKey, json, L2_TTL);
        } catch (JsonProcessingException e) {
            log.warn("L2 캐시 직렬화 실패: {}", cacheKey);
        }

        // 5. L1 (Caffeine) 저장
        l1Cache.put(cacheKey, cachedResult);

        return cachedResult;
    }

    public void evict(String query, int page, int size) {
        String cacheKey = buildCacheKey(query, page, size);
        l1Cache.invalidate(cacheKey);
        redisTemplate.delete(cacheKey);
        evictionPublisher.publish(cacheKey);
    }

    public static final String EVICT_ALL_SIGNAL = "__EVICT_ALL__";

    public void evictAll() {
        l1Cache.invalidateAll();
        var keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        evictionPublisher.publish(EVICT_ALL_SIGNAL);
        log.info("전체 캐시 삭제 완료 (L1 + L2 + Pub/Sub 전파)");
    }

    /**
     * 다른 인스턴스에서 Pub/Sub로 무효화 메시지를 받았을 때 L1만 삭제
     */
    public void evictL1(String cacheKey) {
        if (EVICT_ALL_SIGNAL.equals(cacheKey)) {
            l1Cache.invalidateAll();
            log.info("L1 전체 무효화 via Pub/Sub");
        } else {
            l1Cache.invalidate(cacheKey);
        }
    }

    public CacheStats getStats() {
        double l1Hits = l1HitCounter.count();
        double l2Hits = l2HitCounter.count();
        double misses = cacheMissCounter.count();
        double total = l1Hits + l2Hits + misses;
        var caffeineStats = l1Cache.stats();

        return new CacheStats(l1Hits, l2Hits, misses,
                total > 0 ? (l1Hits + l2Hits) / total * 100 : 0,
                caffeineStats.hitRate() * 100,
                l1Cache.estimatedSize());
    }

    private String buildCacheKey(String query, int page, int size) {
        return CACHE_PREFIX + query + ":" + page + ":" + size;
    }

    public record CacheStats(
            double l1Hits, double l2Hits, double misses,
            double overallHitRatePercent,
            double caffeineHitRatePercent,
            long l1Size
    ) {}
}
