# 이커머스 상품 검색 최적화

MySQL LIKE 검색에서 Elasticsearch 기반 검색 아키텍처로의 전환, 배치 인덱싱 최적화, 다계층 캐시 설계까지의 과정을 구현하고 매 단계마다 실측한 프로젝트.

1000만 건 상품 데이터, 500VU 부하 환경에서 벤치마크를 수행했다.

## 핵심 결과 (1000만 건, 500VU)

| 비교 | Before | After | 개선 |
|------|--------|-------|------|
| 검색 p95 | FULLTEXT 13.84s | ES 396ms | 35배 |
| 검색 QPS | FULLTEXT 28 | ES 1,055 | 38배 |
| 검색 에러율 | FULLTEXT 97% | ES 0% | 해소 |
| ES 인덱싱 | OFFSET 4,493초 | ZeroOffset 298초 | 15배 |
| 캐시 p99 | Redis 단일 157ms | L1+L2 93ms | 41% |

## 아키텍처

```
Client ←→ API Server ←→ Redis (자동완성 + L1/L2 캐시)
                    ←→ Elasticsearch (검색)
                    ←→ MySQL (CRUD)

MySQL → Debezium → Kafka → Consumer → Elasticsearch (CDC 동기화)
```

## 프로젝트 구성

### 01 검색 엔진 전환
MySQL LIKE/FULLTEXT의 한계를 1000만 건 500VU 부하에서 측정하고, Elasticsearch + Nori 형태소 분석기로 전환했다.

- FULLTEXT: N-gram 스코어링의 CPU 비용으로 500VU에서 에러율 97%
- ES 전환 후 p95 396ms, 에러율 0%
- 커넥션 풀은 (core x 2) + spindle 공식에 따라 적정(20)으로 설정. 풀 고갈은 쿼리 실행 시간이 원인
- CDC(Debezium): Binlog 기반 동기화, INSERT 지연 675ms
- 자동완성: Redis Sorted Set으로 ES와 부하 격리. Redis 분리 시 전체 QPS 2배 향상
- Function Score: BM25 x log2p(salesCount) x gauss(30d) x promoted(1.5). log1p의 Zero-Multiply 결함(신상품 점수 0) 발견 후 log2p로 수정
- ES 샤드: 단일 노드에서 3샤드가 1샤드보다 오히려 느림 (merge overhead)

### 02 배치 인덱싱 최적화
ES 1000만 건 인덱싱에서 OFFSET 안티패턴을 발견하고, ZeroOffset으로 15배 개선했다.

- OFFSET(PageRequest.of): 4,493초 (75분). 후반부로 갈수록 악화
- ZeroOffset(WHERE id > lastId): 298초 (5분)
- DataSeeder: rewriteBatchedStatements 적용으로 시딩 48배 개선
- DISABLE KEYS는 InnoDB에서 무효. FULLTEXT DROP → INSERT → 재생성으로 해결

### 03 다계층 캐시
Redis 단일 캐시에서 L1(Caffeine) + L2(Redis) + Pub/Sub 분산 무효화로 확장했다.

- L1(Caffeine, TTL 60s): JVM 로컬, 네트워크 비용 제거
- L2(Redis, TTL 5분): 인스턴스 간 공유
- Redis Pub/Sub: 멀티 인스턴스 L1 무효화 전파
- 현실적 쿼리 분포: 인기 70% + 일반 25% + 유니크 롱테일 5%
- L1+L2 p99 93ms (Redis 단일 157ms 대비 41% 개선)

## 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| DB | MySQL 8.4 |
| Search | Elasticsearch 8 + Nori |
| CDC | Debezium 3.0 + Kafka 4.0 (KRaft) |
| Cache | Caffeine (L1) + Redis 8 (L2) |
| Load Test | k6 |
| Monitoring | Micrometer + Prometheus + Grafana |
| Container | Docker Compose (cAdvisor, mysqld_exporter 포함) |

## 프로젝트 구조

```
src/main/java/com/searchapioptimization/
├── cache/
│   ├── MultiLayerCacheService.java     # L1+L2 다계층 캐시
│   ├── CacheEvictionPublisher.java     # Redis Pub/Sub 발행
│   ├── CacheEvictionSubscriber.java    # Pub/Sub 수신 → L1 무효화
│   └── CacheEvictionConfig.java        # Pub/Sub 리스너 설정
├── config/
│   ├── DataSeeder.java                 # 1000만 건 상품 seed (Zipf 분포)
│   └── ElasticsearchConfig.java        # ES 인덱스 생성 (Nori settings)
├── controller/
│   ├── SearchController.java           # /like, /fulltext, /es, /cached, /multilayer
│   ├── AutocompleteController.java     # /suggest/redis, /suggest/es
│   ├── IndexingController.java         # /index/all, /index/all/zero-offset
│   └── ...
├── domain/
│   ├── Product.java                    # JPA 엔티티
│   └── ProductDocument.java            # ES 문서
├── repository/
│   └── ProductRepository.java          # LIKE, FULLTEXT, Slice, ZeroOffset
└── service/
    ├── SearchService.java              # MySQL 검색
    ├── ElasticsearchService.java       # ES 검색 + Function Score (log2p)
    ├── CachedSearchService.java        # Redis 단일 Cache-Aside
    ├── AutocompleteService.java        # Redis Sorted Set 자동완성
    ├── IndexingService.java            # OFFSET + ZeroOffset 벌크 인덱싱
    └── CdcConsumer.java                # Kafka → ES CDC 동기화

docker-compose.yml                      # MySQL, ES, Kafka, Debezium, Redis,
                                        # Prometheus, Grafana, cAdvisor, mysqld_exporter
k6/                                     # 벤치마크 스크립트 (500VU 통일)
```

## 실행 방법

### 1. 인프라 실행
```bash
docker compose up -d
```

### 2. 데이터 seed (1000만 건, Docker)
```bash
docker compose run --rm app-seed
```

### 3. ES 인덱싱 (ZeroOffset)
```bash
curl -X POST http://localhost:8080/api/index/all/zero-offset
```

### 4. Debezium Connector 등록
```bash
bash scripts/register-connectors.sh
```

### 5. Redis 자동완성 warm-up
```bash
curl -X POST http://localhost:8080/api/suggest/warmup
```

### 6. 벤치마크
```bash
# FULLTEXT 500VU
k6 run k6/bench-fulltext-500vu.js

# ES 500VU
k6 run k6/bench-es-500vu.js

# 다계층 캐시 (현실적 쿼리 분포)
k6 run -e ENDPOINT=multilayer k6/bench-multilayer-realistic.js

# 자동완성 분리 효과
k6 run k6/bench-isolation-redis-split.js
```

### 7. Grafana 대시보드
http://localhost:3000 (admin/admin)
- Search API Benchmark 대시보드: HikariCP, MySQL Threads, HTTP p95/QPS

## 상세 벤치마크

[BENCHMARK.md](BENCHMARK.md) 참조
