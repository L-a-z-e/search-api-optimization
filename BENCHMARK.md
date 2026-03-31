# 검색 API 성능 최적화 — 벤치마크

## 환경
- macOS Darwin 23.3.0 (Apple Silicon)
- Java 17, Spring Boot 3.5.13
- MySQL 8.4 (Docker, innodb_buffer_pool_size=512MB)
- HikariCP: max-pool=20, connection-timeout=3s
- 데이터: 100만 건 상품 (20 카테고리, 500 브랜드)
- k6: ramp-up 50→100→200VU, 60초

---

## Phase 1: MySQL Baseline (LIKE vs FULLTEXT)

### LIKE 검색 (`WHERE name LIKE '%keyword%'`)

| 지표 | 값 |
|------|-----|
| avg | 2.66s |
| p50 (median) | 3.01s |
| p90 | 3.45s |
| p95 | 3.58s |
| QPS | ~50 |
| 에러율 | 17.43% (557/3194) |
| 성공 요청 | 2,637 |

### FULLTEXT 검색 (`MATCH(name) AGAINST(... IN BOOLEAN MODE)`)

| 지표 | 값 |
|------|-----|
| avg | 3.3s |
| p50 (median) | 3.0s |
| p90 | 4.33s |
| p95 | 5.37s |
| min | 13.9ms |
| QPS | ~39 |
| 에러율 | 72.81% (1896/2604) |
| 성공 요청 | 708 |

### 분석

**LIKE**: 100만 건에서 p50 3초, 에러율 17%. Full Table Scan으로 모든 행을 순회.
배민 기준 slow query(0.7초) 대비 4배 이상 초과.

**FULLTEXT**: 의외로 LIKE보다 더 느리고 에러율이 높음 (72.81%).
원인 추정: N-gram FULLTEXT 인덱스의 높은 CPU 부하 (모든 bigram에 대해 역인덱스 탐색 + 스코어링).
min 13.9ms로 단건은 빠르지만, 동시 요청 시 CPU 경합으로 급격히 저하.

**핵심 인사이트**: MySQL FULLTEXT는 단건 성능은 LIKE보다 우수하지만,
동시 요청이 증가하면 CPU 집약적인 N-gram 스코어링으로 오히려 더 많은 에러를 발생시킴.
→ 이것이 Elasticsearch가 필요한 이유: 전용 검색 엔진의 분산 처리 능력이 핵심.

---

## Phase 2: Elasticsearch (Nori 형태소 분석기)

### 구성
- Elasticsearch 8.17.0 (Docker, single node, 512MB heap)
- Nori 형태소 분석기 (decompound_mode: mixed)
- 필드별 가중치: name^3, brand^2, category
- k6: ramp-up 50→100→200→300VU, 100초

### ES 검색 결과

| 지표 | 값 |
|------|-----|
| avg | 7.8ms |
| p50 (median) | 5.12ms |
| p90 | 16.49ms |
| p95 | 20.64ms |
| max | 135.59ms |
| QPS | **1,850** |
| 에러율 | **0.00%** |
| 총 요청 | 185,301 |

### 인덱싱 성능
- 100만 건 bulk indexing: **98초** (batch size 5000)

### Phase 1 대비 개선

| 지표 | LIKE | FULLTEXT | ES | LIKE 대비 개선 |
|------|------|---------|-----|--------------|
| p50 | 3,010ms | 3,000ms | **5.12ms** | **588배** |
| p95 | 3,580ms | 5,370ms | **20.64ms** | **173배** |
| QPS | ~50 | ~39 | **1,850** | **37배** |
| 에러율 | 17.43% | 72.81% | **0.00%** | 에러 제거 |

### 핵심 인사이트

1. **역인덱스의 위력**: 동일 100만건 데이터에서 p50 3초 → 5ms로 588배 개선. Full Table Scan → 포스팅 리스트 룩업의 차이.
2. **분산 처리**: 300VU에서도 에러율 0%, max 135ms. MySQL은 200VU에서 이미 에러 17~72%.
3. **Nori 형태소 분석**: 한국어 복합명사 분해(mixed 모드)로 "삼성전자" → [삼성전자, 삼성, 전자] 검색 가능.

---

## Phase 3: CDC 데이터 동기화 (Debezium + Kafka)

### 구성
- Kafka 4.0.0 (KRaft, single node)
- Debezium 3.0 (quay.io/debezium/connect:3.0)
- MySQL Source Connector (binlog CDC, schema_only snapshot)
- Spring Kafka Consumer → ES 동기화

### CDC 동기화 지연 (INSERT → ES 반영)

| Run | lag (ms) |
|-----|----------|
| 1 | 685 |
| 2 | 677 |
| 3 | 667 |
| 4 | 665 |
| 5 | 680 |
| **평균** | **~675ms** |

### 삭제 이벤트 동기화

| 테스트 | MySQL 삭제 → ES 삭제 lag |
|--------|------------------------|
| delete-test | **1,024ms** |

### 핵심 인사이트

1. **CDC < 1초**: MySQL INSERT → Debezium binlog → Kafka → Spring Consumer → ES 반영까지 평균 675ms.
2. **삭제 감지**: Debezium `__deleted` 필드로 tombstone 이벤트 처리. MySQL DELETE가 ES에 ~1초 후 반영.
3. **DB 부하 최소**: binlog 읽기만 하므로 원본 MySQL에 추가 쿼리 부하 없음.
4. **이중 쓰기 대비 장점**: 트랜잭션 불일치 불가능 (CDC는 커밋된 데이터만 캡처), 삭제 자동 감지.

---

## Phase 4: 자동완성 (Redis Sorted Set vs ES Match)

### 구성
- Redis 8 (Docker, AOF 활성)
- Redis warm-up: 브랜드 Top 100 + 상품명 접두사 Top 1000 = 1,058건, 3.1초
- 접두사별 Sorted Set (score = 판매량 합산)

### 단건 응답 비교

| 방식 | latency | 비고 |
|------|---------|------|
| **Redis Sorted Set** | **4ms** | 인메모리 접두사 매칭 |
| ES match query | 119ms | 역인덱스 + BM25 스코어링 |
| 개선 | **30배** | |

### 300VU 부하 비교

| 방식 | avg | p50 | p95 | max | QPS | 에러율 |
|------|-----|-----|-----|-----|-----|--------|
| **Redis** | 2.02ms | 1.71ms | 4.30ms | 27ms | **3,335** | 0% |
| ES match | 2.01ms | 1.45ms | 4.52ms | 43ms | 3,347 | 0% |

### 핵심 인사이트

1. **단건 latency**: Redis 4ms vs ES 119ms = 30배. 사용자 타이핑 중 체감 차이 큼.
2. **고부하에서는 비슷**: 300VU에서 둘 다 avg ~2ms. Spring+네트워크 오버헤드가 지배적.
3. **Redis의 실무적 장점**: 인기 검색어 warm-up(1,058건 3초)으로 캐시 워밍이 빠르고, ES 부하를 분리. 실제 서비스에서는 자동완성 트래픽이 본 검색의 5~10배이므로 ES와 분리하는 것이 중요.
4. **warm-up 전략**: 판매량 기반 Zipf 분포에서 상위 1,000개만 적재해도 대부분의 자동완성 커버.

---

## Phase 비교표 (누적)

| 지표 | Phase 1 LIKE | Phase 1 FULLTEXT | Phase 2 ES | 개선 (LIKE 대비) |
|------|-------------|-----------------|-----------|----------------|
| p50 | 3.01s | 3.0s | **5.12ms** | 588x |
| p95 | 3.58s | 5.37s | **20.64ms** | 173x |
| QPS | ~50 | ~39 | **1,850** | 37x |
| 에러율 | 17.43% | 72.81% | **0.00%** | 에러 제거 |
