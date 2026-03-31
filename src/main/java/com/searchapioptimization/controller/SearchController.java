package com.searchapioptimization.controller;

import com.searchapioptimization.controller.dto.SearchResponse;
import com.searchapioptimization.service.CachedSearchService;
import com.searchapioptimization.service.ElasticsearchService;
import com.searchapioptimization.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final ElasticsearchService elasticsearchService;
    private final CachedSearchService cachedSearchService;

    @GetMapping("/like")
    public SearchResponse searchByLike(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchService.searchByLike(q, page, size);
    }

    @GetMapping("/fulltext")
    public SearchResponse searchByFulltext(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchService.searchByFulltext(q, page, size);
    }

    @GetMapping("/es")
    public SearchResponse searchByElasticsearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return elasticsearchService.search(q, page, size);
    }

    @GetMapping("/es/ranked")
    public SearchResponse searchByFunctionScore(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return elasticsearchService.searchWithFunctionScore(q, page, size);
    }

    @GetMapping("/cached")
    public SearchResponse searchCached(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return cachedSearchService.search(q, page, size);
    }

    @GetMapping("/cache/stats")
    public CachedSearchService.CacheStats cacheStats() {
        return cachedSearchService.getStats();
    }

    @DeleteMapping("/cache")
    public Map<String, String> evictCache() {
        cachedSearchService.evictAll();
        return Map.of("status", "evicted");
    }
}
