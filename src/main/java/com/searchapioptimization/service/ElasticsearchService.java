package com.searchapioptimization.service;

import com.searchapioptimization.controller.dto.SearchResponse;
import com.searchapioptimization.controller.dto.SearchResponse.ProductDto;
import com.searchapioptimization.domain.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.json.JsonData;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchService {

    private final ElasticsearchOperations operations;

    public SearchResponse search(String query, int page, int size) {
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .multiMatch(mm -> mm
                                .query(query)
                                .fields("name^3", "brand^2", "category")
                                .type(TextQueryType.BestFields)
                        )
                )
                .withPageable(PageRequest.of(page, size))
                .build();

        return executeSearch(searchQuery, "ELASTICSEARCH", page, size);
    }

    public SearchResponse searchWithFunctionScore(String query, int page, int size) {
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .functionScore(fs -> fs
                                .query(innerQ -> innerQ
                                        .multiMatch(mm -> mm
                                                .query(query)
                                                .fields("name^3", "brand^2", "category")
                                                .type(TextQueryType.BestFields)
                                        )
                                )
                                // 판매량 반영 (log1p)
                                .functions(fn -> fn.fieldValueFactor(fvf -> fvf
                                        .field("salesCount")
                                        .modifier(co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier.Log1p)
                                        .factor(0.5)
                                ))
                                // 프로모션 상품 부스팅
                                .functions(fn -> fn
                                        .filter(f -> f.term(t -> t.field("promoted").value(true)))
                                        .weight(1.5)
                                )
                                .scoreMode(co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode.Multiply)
                                .boostMode(co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode.Multiply)
                        )
                )
                .withPageable(PageRequest.of(page, size))
                .build();

        return executeSearch(searchQuery, "FUNCTION_SCORE", page, size);
    }

    private SearchResponse executeSearch(NativeQuery searchQuery, String searchType, int page, int size) {
        SearchHits<ProductDocument> hits = operations.search(searchQuery, ProductDocument.class);

        var products = hits.getSearchHits().stream()
                .map(hit -> {
                    ProductDocument doc = hit.getContent();
                    return ProductDto.builder()
                            .id(doc.getId())
                            .name(doc.getName())
                            .brand(doc.getBrand())
                            .category(doc.getCategory())
                            .price(doc.getPrice())
                            .salesCount(doc.getSalesCount())
                            .createdAt(doc.getCreatedAt())
                            .build();
                })
                .toList();

        return SearchResponse.builder()
                .products(products)
                .page(page)
                .size(size)
                .totalElements(hits.getTotalHits())
                .totalPages((int) Math.ceil((double) hits.getTotalHits() / size))
                .searchType(searchType)
                .build();
    }
}
