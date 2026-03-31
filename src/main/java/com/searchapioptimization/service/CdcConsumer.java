package com.searchapioptimization.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchapioptimization.domain.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class CdcConsumer {

    private final ElasticsearchOperations operations;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "search.search_db.product", groupId = "search-es-sync")
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            // Debezium ExtractNewRecordState: __deleted 필드로 삭제 감지
            boolean deleted = node.has("__deleted") && "true".equals(node.get("__deleted").asText());

            long id = node.get("id").asLong();

            if (deleted) {
                operations.delete(String.valueOf(id), ProductDocument.class);
                log.debug("CDC DELETE: id={}", id);
                return;
            }

            // epoch millis → LocalDateTime 변환
            LocalDateTime createdAt = node.has("created_at") && !node.get("created_at").isNull()
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(node.get("created_at").asLong()), ZoneId.systemDefault())
                    : LocalDateTime.now();

            ProductDocument doc = ProductDocument.builder()
                    .id(id)
                    .name(node.get("name").asText())
                    .brand(node.get("brand").asText())
                    .category(node.get("category").asText())
                    .price(node.get("price").asLong())
                    .salesCount(node.has("sales_count") ? node.get("sales_count").asLong() : 0L)
                    .promoted(node.has("promoted") && node.get("promoted").asInt() == 1)
                    .createdAt(createdAt)
                    .build();

            operations.index(new IndexQueryBuilder()
                    .withId(String.valueOf(id))
                    .withObject(doc)
                    .build(), operations.getIndexCoordinatesFor(ProductDocument.class));

            log.debug("CDC UPSERT: id={}, name={}", id, doc.getName());

        } catch (Exception e) {
            log.error("CDC 메시지 처리 실패: {}", message, e);
        }
    }
}
