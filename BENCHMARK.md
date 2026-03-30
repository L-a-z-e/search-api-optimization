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

## Phase 비교표 (누적)

| 지표 | Phase 1 LIKE | Phase 1 FULLTEXT | Phase 2 ES | ... |
|------|-------------|-----------------|-----------|-----|
| p50 | 3.01s | 3.0s | TBD | |
| p95 | 3.58s | 5.37s | TBD | |
| QPS | ~50 | ~39 | TBD | |
| 에러율 | 17.43% | 72.81% | TBD | |
