package com.searchapioptimization.controller;

import com.searchapioptimization.controller.dto.SearchResponse;
import com.searchapioptimization.service.ElasticsearchService;
import com.searchapioptimization.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final ElasticsearchService elasticsearchService;

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
}
