package com.searchapioptimization.repository;

import com.searchapioptimization.config.MySQLTestConfig;
import com.searchapioptimization.domain.Product;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductRepositoryTest extends MySQLTestConfig {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        // FULLTEXT 인덱스 추가 (DDL auto로는 생성 안 됨)
        try {
            jdbcTemplate.execute("ALTER TABLE product ADD FULLTEXT INDEX ft_name (name) WITH PARSER ngram");
        } catch (Exception e) {
            // 이미 존재하면 무시
        }

        List<Product> products = List.of(
                Product.builder().name("삼성 갤럭시 S24 울트라 256GB").brand("삼성").category("전자기기").price(1500000L).salesCount(50000L).createdAt(LocalDateTime.now()).build(),
                Product.builder().name("삼성전자 갤럭시 버즈3 프로").brand("삼성").category("전자기기").price(300000L).salesCount(20000L).createdAt(LocalDateTime.now()).build(),
                Product.builder().name("애플 아이폰 16 프로맥스 512GB").brand("애플").category("전자기기").price(1900000L).salesCount(80000L).createdAt(LocalDateTime.now()).build(),
                Product.builder().name("나이키 에어맥스 97 블랙").brand("나이키").category("스포츠").price(189000L).salesCount(30000L).createdAt(LocalDateTime.now()).build(),
                Product.builder().name("CJ 비비고 왕교자 만두 1.05kg").brand("CJ").category("식품").price(8900L).salesCount(100000L).createdAt(LocalDateTime.now()).build()
        );
        productRepository.saveAll(products);
    }

    @Test
    @DisplayName("LIKE 검색: '삼성' 포함된 상품을 찾는다")
    void likeSearch_findsSamsungProducts() {
        Page<Product> result = productRepository.findByNameContaining("삼성", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allSatisfy(p ->
                assertThat(p.getName()).contains("삼성")
        );
    }

    @Test
    @DisplayName("LIKE 검색: '갤럭시' 포함된 상품을 찾는다")
    void likeSearch_findsGalaxyProducts() {
        Page<Product> result = productRepository.findByNameContaining("갤럭시", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("LIKE 검색: 존재하지 않는 키워드는 빈 결과")
    void likeSearch_noResults() {
        Page<Product> result = productRepository.findByNameContaining("존재하지않는상품", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("FULLTEXT 검색: '삼성' 매칭 상품을 찾는다")
    void fulltextSearch_findsSamsungProducts() {
        Page<Product> result = productRepository.findByFulltext("삼성", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(result.getContent()).allSatisfy(p ->
                assertThat(p.getName()).containsIgnoringCase("삼성")
        );
    }

    @Test
    @DisplayName("FULLTEXT 검색: '갤럭시' 매칭 상품을 찾는다")
    void fulltextSearch_findsGalaxyProducts() {
        Page<Product> result = productRepository.findByFulltext("갤럭시", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("LIKE 검색: 페이지네이션이 동작한다")
    void likeSearch_pagination() {
        Page<Product> page0 = productRepository.findByNameContaining("삼성", PageRequest.of(0, 1));
        Page<Product> page1 = productRepository.findByNameContaining("삼성", PageRequest.of(1, 1));

        assertThat(page0.getContent()).hasSize(1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
    }
}
