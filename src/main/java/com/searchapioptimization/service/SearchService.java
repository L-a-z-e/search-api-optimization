package com.searchapioptimization.service;

import com.searchapioptimization.controller.dto.SearchResponse;
import com.searchapioptimization.controller.dto.SearchResponse.ProductDto;
import com.searchapioptimization.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final ProductRepository productRepository;

    public SearchResponse searchByLike(String query, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = productRepository.findByNameContaining(query, pageable);

        return SearchResponse.builder()
                .products(result.getContent().stream().map(ProductDto::from).toList())
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .searchType("LIKE")
                .build();
    }

    public SearchResponse searchByFulltext(String query, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = productRepository.findByFulltext(query, pageable);

        return SearchResponse.builder()
                .products(result.getContent().stream().map(ProductDto::from).toList())
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .searchType("FULLTEXT")
                .build();
    }
}
