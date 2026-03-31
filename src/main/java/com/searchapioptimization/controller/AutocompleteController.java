package com.searchapioptimization.controller;

import com.searchapioptimization.service.AutocompleteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suggest")
@RequiredArgsConstructor
public class AutocompleteController {

    private final AutocompleteService autocompleteService;

    @GetMapping("/redis")
    public Map<String, Object> suggestByRedis(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        long start = System.nanoTime();
        List<String> results = autocompleteService.suggestByRedis(q, limit);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return Map.of("suggestions", results, "type", "REDIS", "latencyMs", elapsed);
    }

    @GetMapping("/es")
    public Map<String, Object> suggestByEs(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        long start = System.nanoTime();
        List<String> results = autocompleteService.suggestByEsMatch(q, limit);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return Map.of("suggestions", results, "type", "ES_MATCH", "latencyMs", elapsed);
    }

    @PostMapping("/warmup")
    public Map<String, Object> warmUp() {
        return autocompleteService.warmUpRedis();
    }
}
