# 검색 API 성능 최적화

MySQL LIKE 쿼리에서 Elasticsearch 기반 검색 아키텍처로의 전환 과정을 **7단계로 구현하고 매 단계마다 실측**한 프로젝트.

100만 건 상품 데이터, 200~500VU 부하 환경에서 **p50 3,010ms → 1.57ms (1,917배 개선)** 을 달성.

## 핵심 결과

| 지표 | Phase 1 (LIKE) | Phase 7 (최종) | 개선 |
|------|---------------|---------------|------|
| p50 latency | 3,010ms | **1.57ms** | **1,917x** |
| p95 latency | 3,580ms | **4.93ms** | **726x** |
| QPS | ~50 | **3,175** | **63x** |
| 에러율 | 17.43% | **0%** | 에러 제거 |

## 아키텍처

```
┌────────┐   ┌──────────┐   ┌──────────────────────────┐
│ Client │──→│ API      │──→│ Redis 자동완성 (~4ms)     │
│        │   │          │──→│ Redis 검색캐시 (hit→~1ms) │
│        │   │          │──→│ ES 본 검색 (miss→~5ms)    │
└────────┘   └──────────┘   └──────────────────────────┘
                                       ↑
             ┌─────────────────────────┼────────────┐
             │ MySQL → Debezium → Kafka → ES Sync   │
             │ (CDC, avg 675ms lag)                  │
             └──────────────────────────────────────┘
```

## Phase별 구현 내용

### Phase 1: MySQL Baseline
MySQL `LIKE '%keyword%'`의 한계를 수치로 증명.
- LIKE: p50 3.01s, 에러율 17% (200VU)
- FULLTEXT (N-gram): p50 3.0s, **에러율 72%** (CPU 경합)
- EXPLAIN: `type: ALL`, 89만 행 Full Table Scan

### Phase 2: Elasticsearch + Nori
역인덱스 기반 검색으로 전환. 한국어 형태소 분석기(Nori) 적용.
- p50 **5.12ms** (LIKE 대비 588배), QPS **1,850** (37배)
- Nori `decompound_mode: mixed`: "삼성전자" → [삼성전자, 삼성, 전자]
- Multi-match + 필드별 가중치: `name^3, brand^2, category`

### Phase 3: CDC 데이터 동기화
Debezium CDC로 MySQL → ES 실시간 동기화.
- MySQL binlog → Debezium → Kafka → Spring Consumer → ES
- INSERT 동기화 지연: 평균 **675ms**
- DELETE 감지: **1,024ms** (`__deleted` 필드 기반)
- 이중 쓰기 대비 장점: ES 2PC 미지원 → CDC만이 정합성 보장

### Phase 4: 자동완성
Redis Sorted Set으로 접두사 기반 자동완성 구현.
- Redis: **4ms** vs ES match: 119ms (**30배**)
- 인기 브랜드/상품명 warm-up: 1,058건, 3초
- 접두사별 Sorted Set + 판매량 score

### Phase 5: 검색 결과 캐싱
Redis Cache-Aside 패턴으로 검색 결과 캐싱.
- 캐시 적중률 **99.98%** (Zipf 분포 쿼리셋)
- QPS 1,922 → **3,324** (1.7배)
- Micrometer 커스텀 메트릭으로 적중률 모니터링

### Phase 6: Function Score 랭킹
BM25 텍스트 점수에 비즈니스 로직을 곱하여 랭킹 개선.
- BM25 × log1p(salesCount) × promoted(1.5x)
- 판매량 높은 최신 상품이 상위 노출

### Phase 7: 종합 벤치마크
전체 스택 동시 동작 (500VU, 60초).
- p50 **1.57ms**, p95 4.93ms, QPS **3,175**, 에러율 **0%**

## 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5.13 |
| DB | MySQL 8.4 |
| Search Engine | Elasticsearch 8.17 + Nori |
| CDC | Debezium 3.0 + Kafka 4.0 (KRaft) |
| Cache | Redis 8 |
| Load Test | k6 |
| Monitoring | Micrometer + Prometheus |
| Container | Docker Compose |
| Test | Testcontainers + JUnit 5 |

## 프로젝트 구조

```
src/main/java/com/searchapioptimization/
├── config/
│   ├── DataSeeder.java              # 100만 건 상품 seed (Zipf 분포)
│   └── ElasticsearchConfig.java     # ES 인덱스 생성 (Nori settings)
├── controller/
│   ├── SearchController.java        # /like, /fulltext, /es, /cached, /es/ranked
│   ├── AutocompleteController.java  # /suggest/redis, /suggest/es
│   ├── ProductController.java       # CRUD API
│   ├── IndexingController.java      # MySQL → ES 벌크 인덱싱
│   └── CdcVerifyController.java     # CDC lag 측정, 삭제 검증
├── domain/
│   ├── Product.java                 # JPA 엔티티
│   └── ProductDocument.java         # ES 문서
├── repository/
│   ├── ProductRepository.java       # LIKE + FULLTEXT
│   └── ProductDocumentRepository.java
└── service/
    ├── SearchService.java           # MySQL 검색
    ├── ElasticsearchService.java    # ES 검색 + Function Score
    ├── CachedSearchService.java     # Redis Cache-Aside
    ├── AutocompleteService.java     # Redis Sorted Set 자동완성
    ├── IndexingService.java         # 벌크 인덱싱
    ├── ProductService.java          # CRUD
    └── CdcConsumer.java             # Kafka → ES 동기화

docker/
├── elasticsearch/Dockerfile         # ES + Nori 플러그인
├── mysql/init.sql                   # DDL + FULLTEXT 인덱스
├── kafka-connect/
│   ├── Dockerfile                   # Debezium + ES Sink
│   └── connectors/                  # Connector JSON 설정
└── prometheus/prometheus.yml

k6/                                  # Phase별 부하 테스트 스크립트
```

## 실행 방법

### 1. 인프라 실행
```bash
docker compose up -d
```

### 2. 데이터 seed (100만 건)
```bash
./gradlew bootRun --args='--spring.profiles.active=seed'
```

### 3. ES 인덱싱
```bash
curl -X POST http://localhost:8080/api/index/all
```

### 4. Debezium Connector 등록
```bash
bash scripts/register-connectors.sh
```

### 5. Redis 자동완성 warm-up
```bash
curl -X POST http://localhost:8080/api/suggest/warmup
```

### 6. 부하 테스트
```bash
# Phase 1: MySQL LIKE
k6 run k6/phase1-like-only.js

# Phase 2: Elasticsearch
k6 run k6/phase2-es.js

# Phase 7: 종합 (캐시 + ES)
k6 run k6/phase7-tuning.js
```

## 발견한 인사이트

1. **FULLTEXT가 LIKE보다 나쁠 수 있다**: 단건은 빠르지만 200VU 동시 부하에서 N-gram 스코어링의 CPU 경합으로 에러율 72% (LIKE 17%).

2. **CDC < 1초**: MySQL INSERT → binlog → Debezium → Kafka → Spring Consumer → ES 반영까지 평균 675ms. 이중 쓰기는 ES 2PC 미지원으로 정합성 보장 불가.

3. **Zipf 분포가 캐시를 만든다**: 33종 쿼리셋에서 적중률 99.98%. 상위 20% 검색어 = 80% 트래픽이라 소수만 캐싱해도 극적 효과.

4. **ES는 이미 빠르다**: ES p50 5ms에서 Redis 캐시 p50 1.8ms로 절대값 차이는 적음. 캐시의 핵심은 latency 감소보다 **ES 부하 분산**.

## 상세 벤치마크

[BENCHMARK.md](BENCHMARK.md) 참조
