package com.searchapioptimization.controller;

import com.searchapioptimization.config.MySQLTestConfig;
import com.searchapioptimization.domain.Product;
import com.searchapioptimization.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerTest extends MySQLTestConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        try {
            jdbcTemplate.execute("ALTER TABLE product ADD FULLTEXT INDEX ft_name (name) WITH PARSER ngram");
        } catch (Exception e) {
            // 이미 존재하면 무시
        }

        List<Product> products = List.of(
                Product.builder().name("삼성 갤럭시 S24 울트라").brand("삼성").category("전자기기").price(1500000L).salesCount(50000L).createdAt(LocalDateTime.now()).build(),
                Product.builder().name("애플 아이폰 16 프로맥스").brand("애플").category("전자기기").price(1900000L).salesCount(80000L).createdAt(LocalDateTime.now()).build(),
                Product.builder().name("나이키 에어맥스 97").brand("나이키").category("스포츠").price(189000L).salesCount(30000L).createdAt(LocalDateTime.now()).build()
        );
        productRepository.saveAll(products);
    }

    @Test
    @DisplayName("GET /api/search/like — LIKE 검색 API")
    void searchByLike() throws Exception {
        mockMvc.perform(get("/api/search/like")
                        .param("q", "삼성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchType").value("LIKE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.products[0].name", containsString("삼성")));
    }

    @Test
    @DisplayName("GET /api/search/fulltext — FULLTEXT 검색 API")
    void searchByFulltext() throws Exception {
        mockMvc.perform(get("/api/search/fulltext")
                        .param("q", "삼성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchType").value("FULLTEXT"))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.products", not(empty())));
    }

    @Test
    @DisplayName("검색 API — 빈 결과 반환")
    void searchNoResults() throws Exception {
        mockMvc.perform(get("/api/search/like")
                        .param("q", "없는상품"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.products", empty()));
    }

    @Test
    @DisplayName("검색 API — 페이지네이션 파라미터")
    void searchWithPagination() throws Exception {
        mockMvc.perform(get("/api/search/like")
                        .param("q", "삼성")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.products", hasSize(1)));
    }
}
