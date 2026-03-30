package com.searchapioptimization.config;

import com.searchapioptimization.domain.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("!test")
public class ElasticsearchConfig {

    private final ElasticsearchOperations operations;

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexIfNotExists() {
        IndexOperations indexOps = operations.indexOps(ProductDocument.class);

        if (!indexOps.exists()) {
            try {
                String settings = new ClassPathResource("elasticsearch/settings.json")
                        .getContentAsString(StandardCharsets.UTF_8);
                String mappings = new ClassPathResource("elasticsearch/mappings.json")
                        .getContentAsString(StandardCharsets.UTF_8);

                indexOps.create(Document.parse(settings));
                indexOps.putMapping(Document.parse(mappings));

                log.info("ES 인덱스 'products' 생성 완료 (Nori analyzer)");
            } catch (IOException e) {
                log.error("ES 인덱스 생성 실패", e);
            }
        } else {
            log.info("ES 인덱스 'products' 이미 존재");
        }
    }
}
