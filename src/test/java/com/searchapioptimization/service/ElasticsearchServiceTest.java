package com.searchapioptimization.service;

import com.searchapioptimization.config.MySQLTestConfig;
import com.searchapioptimization.controller.dto.SearchResponse;
import com.searchapioptimization.domain.ProductDocument;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ElasticsearchServiceTest extends MySQLTestConfig {

    @Container
    static ElasticsearchContainer esContainer = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.17.0")
    )
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m");

    @DynamicPropertySource
    static void esProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", esContainer::getHttpHostAddress);
    }

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchOperations operations;

    @BeforeEach
    void setUp() {
        // Testcontainers ES doesn't have Nori plugin — use default analyzer
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping();

        List<ProductDocument> docs = List.of(
                ProductDocument.builder().id(1L).name("삼성 갤럭시 S24 울트라 256GB").brand("삼성").category("전자기기").price(1500000L).salesCount(50000L).promoted(false).createdAt(LocalDateTime.now()).build(),
                ProductDocument.builder().id(2L).name("삼성전자 갤럭시 버즈3 프로").brand("삼성").category("전자기기").price(300000L).salesCount(20000L).promoted(false).createdAt(LocalDateTime.now()).build(),
                ProductDocument.builder().id(3L).name("애플 아이폰 16 프로맥스 512GB").brand("애플").category("전자기기").price(1900000L).salesCount(80000L).promoted(true).createdAt(LocalDateTime.now()).build(),
                ProductDocument.builder().id(4L).name("나이키 에어맥스 97 블랙").brand("나이키").category("스포츠").price(189000L).salesCount(30000L).promoted(false).createdAt(LocalDateTime.now()).build(),
                ProductDocument.builder().id(5L).name("CJ 비비고 왕교자 만두 1.05kg").brand("CJ").category("식품").price(8900L).salesCount(100000L).promoted(false).createdAt(LocalDateTime.now()).build()
        );

        List<IndexQuery> queries = docs.stream()
                .map(d -> new IndexQueryBuilder().withId(String.valueOf(d.getId())).withObject(d).build())
                .toList();
        operations.bulkIndex(queries, operations.getIndexCoordinatesFor(ProductDocument.class));

        // refresh to make searchable
        operations.indexOps(ProductDocument.class).refresh();
    }

    @Test
    @DisplayName("ES 검색: '삼성' 매칭 상품을 찾는다")
    void search_findsSamsungProducts() {
        SearchResponse result = elasticsearchService.search("삼성", 0, 20);

        assertThat(result.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(result.searchType()).isEqualTo("ELASTICSEARCH");
        assertThat(result.products()).allSatisfy(p ->
                assertThat(p.name()).containsIgnoringCase("삼성")
        );
    }

    @Test
    @DisplayName("ES 검색: '갤럭시' 매칭 상품을 찾는다")
    void search_findsGalaxyProducts() {
        SearchResponse result = elasticsearchService.search("갤럭시", 0, 20);

        assertThat(result.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("ES 검색: 빈 결과 반환")
    void search_noResults() {
        SearchResponse result = elasticsearchService.search("존재하지않는상품명", 0, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.products()).isEmpty();
    }

    @Test
    @DisplayName("ES 검색: 필드별 가중치 — name 매칭이 우선")
    void search_nameBoostHigherThanBrand() {
        SearchResponse result = elasticsearchService.search("나이키", 0, 20);

        assertThat(result.totalElements()).isGreaterThanOrEqualTo(1);
        // 나이키가 이름에 포함된 상품이 첫 번째
        assertThat(result.products().get(0).name()).contains("나이키");
    }

    @Test
    @DisplayName("ES 검색: 페이지네이션 동작")
    void search_pagination() {
        SearchResponse page0 = elasticsearchService.search("삼성", 0, 1);
        SearchResponse page1 = elasticsearchService.search("삼성", 1, 1);

        assertThat(page0.products()).hasSize(1);
        assertThat(page1.products()).hasSize(1);
        assertThat(page0.products().get(0).id()).isNotEqualTo(page1.products().get(0).id());
    }
}
