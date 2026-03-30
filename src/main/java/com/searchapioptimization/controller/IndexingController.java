package com.searchapioptimization.controller;

import com.searchapioptimization.service.IndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/index")
@RequiredArgsConstructor
public class IndexingController {

    private final IndexingService indexingService;

    @PostMapping("/all")
    public Map<String, Object> indexAll() {
        return indexingService.indexAll();
    }
}
