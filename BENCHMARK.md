# 검색 API 성능 최적화 — 벤치마크

## 환경
- macOS Darwin 23.3.0 (Apple Silicon, 10 cores / 47GB Docker 할당)
- Java 21, Spring Boot 3.5
- MySQL 8.4 (Docker, innodb_buffer_pool_size=1G, max_connections=200, ngram_token_size=2)
- HikariCP: max-pool=20, min-idle=20, connection-timeout=5s, leak-detection=2s
  - 풀 사이징 근거: (core×2)+spindle = (10×2)+1 = 21. 20은 공식 기반 적정값
- Elasticsearch 8.17 (Docker, single node, 512MB heap, Nori plugin)
- Redis 8 (Docker, AOF)
- 데이터: 100만 건 상품 (20 카테고리, 50 브랜드, Zipf 분포 salesCount)

---

## Phase 0: 단건 쿼리 시간 측정 (부하 없음)

부하 테스트 전에 각 방식의 단건 쿼리 시간을 측정하여 쿼리 자체의 비용을 확인한다.
FULLTEXT Page(SELECT+COUNT 2회)와 Slice(SELECT만 1회)를 구분하여 COUNT 비용도 확인한다.

### 측정 결과 (curl 응답 시간, warm-up 2회 후)

| 검색어 | 유형 | FULLTEXT Page | FULLTEXT Slice | LIKE |
|--------|------|--------------|----------------|------|
| 삼성 | 인기 (2글자) | 83ms | 48ms | 214ms |
| 갤럭시 | 인기 (3글자) | 312ms | 144ms | 214ms |
| 아이폰 | 인기 (3글자) | 88ms | 46ms | 213ms |
| 나이키 | 인기 (3글자) | 268ms | 144ms | 212ms |
| 노트북 | 인기 (3글자) | 106ms | 53ms | 208ms |
| 블루투스 이어폰 | 보통 | 44ms | 28ms | 307ms |
| 여름 원피스 | 보통 | 30ms | 24ms | 308ms |
| 무선 청소기 | 보통 | 90ms | 68ms | 310ms |
| 삼성 갤럭시 S24 울트라 256GB | 롱테일 | 839ms | 420ms | 210ms |
| 나이키 에어맥스 97 블랙 | 롱테일 | 524ms | 284ms | 212ms |

### 분석

1. **LIKE는 검색어와 무관하게 210~310ms로 일정하다.** Full Table Scan이므로 데이터 크기에만 의존한다. 복합어("블루투스 이어폰")가 약간 느린 건 LIKE '%블루투스%이어폰%' 패턴 매칭 비용.

2. **FULLTEXT는 검색어 길이에 비례해서 느려진다.** 2글자(삼성) 83ms → 롱테일(삼성 갤럭시 S24 울트라 256GB) 839ms. N-gram 토큰 수가 늘어날수록 역인덱스 탐색 + 스코어링 비용이 증가한다.

3. **복합어("블루투스 이어폰" 44ms)가 단일어("갤럭시" 312ms)보다 빠르다.** 직관에 반하지만 원인은 매칭 건수 차이. "블루투스 이어폰"은 N-gram 토큰이 "블루", "루투", "투스", "이어", "어폰"으로 5개이고 전부 AND 매칭해야 하므로 결과 집합이 극히 적다. 반면 "갤럭시"는 "갤럭", "럭시" 2개 토큰만으로 수만 건이 매칭된다. FULLTEXT 스코어링 비용은 매칭 건수에 비례하므로, 토큰이 많아도 결과가 적으면 오히려 빠르다. LIKE는 어차피 Full Scan이라 매칭 건수와 무관하게 일정하다.

4. **Slice(COUNT 제거)로 약 50% 단축된다.** Page는 SELECT + COUNT 2회 실행. COUNT가 전체 매칭 건수를 세야 하므로 비용이 크다. 롱테일: 839ms → 420ms.

5. **500VU 부하에서의 예상 처리량:**
   - FULLTEXT Page 평균 ~200ms 가정: 20 커넥션 / 0.2초 = 100 QPS
   - FULLTEXT Slice 평균 ~100ms 가정: 20 커넥션 / 0.1초 = 200 QPS
   - 500VU 요구량 ~833 QPS: 두 방식 모두 커넥션 포화 예상
   - 커넥션 풀은 공식 기반 적정 크기(20). 풀을 늘려도 MySQL CPU가 병목이라 효과가 제한적.

---

## Phase 1: MySQL vs Elasticsearch (500VU 통일 비교)

### 테스트 조건
- 500VU, ramp-up 30초 + sustain 60초 = 총 90초
- HikariCP: pool=20 (공식 기반), min-idle=20 (고정 풀), timeout=5s
- MySQL: buffer_pool=1G, max_connections=200, ngram_token_size=2
- ES: single node, 512MB heap, Nori analyzer
- 쿼리: Zipf 분포 (인기 60% + 보통 30% + 롱테일 10%, 33종)

### 500VU 부하 비교

| 방식 | p50 | p95 | QPS | 에러율 | 총 요청 |
|------|-----|-----|-----|--------|--------|
| LIKE (Full Scan) | 12.24s | 12.70s | 40 | 0.41% | 4,103 |
| FULLTEXT (Page, SELECT+COUNT) | 13.07s | 15.57s | 32 | 66.94% | 3,413 |
| FULLTEXT (Slice, SELECT만) | 13.02s | 15.29s | 34 | 37.88% | 3,534 |
| **Elasticsearch + Nori** | **50.61ms** | **77.36ms** | **2,735** | **0%** | **246,633** |

### 분석

1. **ES는 MySQL 대비 p95 기준 200배 빠르다.** 15초 → 77ms. 같은 500VU, 같은 쿼리셋에서 측정한 공정 비교.

2. **FULLTEXT Slice(COUNT 제거)는 에러율만 67%→38%로 줄였고 p50/p95는 거의 같다.** COUNT 쿼리가 아니라 FULLTEXT 검색 쿼리 자체가 병목. Phase 0 단건 측정에서 롱테일 검색어가 839ms 걸리는 것과 일치한다.

3. **FULLTEXT가 LIKE보다 더 나쁘다.** 에러율 67% vs 0.4%. LIKE는 단순 Full Scan이라 CPU 부하가 상대적으로 낮고, FULLTEXT는 N-gram 토큰 매칭+스코어링이 CPU 집약적이라 동시 부하에서 더 빨리 무너진다.

4. **커넥션 풀은 병목이 아니다.** pool=20은 (core×2)+spindle 공식 기반 적정값이다. 풀을 늘려도 MySQL CPU가 쿼리를 처리하는 속도가 병목이므로 의미 없다. Phase 0 단건 측정에서 FULLTEXT가 30ms~839ms(검색어 의존)이므로, pool 20개로 최대 24~667 QPS. 500VU 요구(~833 QPS)를 충족할 수 없다.

5. **ES는 HikariCP를 거치지 않는다.** ES REST Client로 직접 통신하므로 MySQL 커넥션 풀 문제 자체가 없다. 이것이 "커넥션 풀을 늘려라"가 해법이 아닌 이유.

---

## Phase 1 (이전 200VU 참고, 아래는 이전 데이터)

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

### 부하 분리 효과 테스트 (자동완성 300VU + 메인검색 200VU 동시)

자동완성의 진짜 가치는 latency가 아니라 ES 부하 분리다. 자동완성 트래픽(300VU)과 메인 검색(200VU)을 동시에 보내서 메인 검색 p95를 비교한다.

| 시나리오 | 자동완성 경로 | 메인검색 p50 | 메인검색 p95 | 자동완성 p95 | 총 QPS |
|---------|-------------|-------------|-------------|-------------|--------|
| A: 전부 ES | /api/suggest/es | 7.08ms | **20.16ms** | 17.46ms | 6,063 |
| B: Redis 분리 | /api/suggest/redis | 2.86ms | **6.39ms** | 2.06ms | 6,641 |
| 개선 | | 2.5배 | **3.2배** | 8.5배 | 1.1배 |

### 핵심 인사이트

1. **단건 latency**: Redis 4ms vs ES 119ms = 30배. 사용자 타이핑 중 체감 차이 큼.
2. **고부하에서 단독 비교는 비슷**: 300VU에서 둘 다 avg ~2ms. Spring+네트워크 오버헤드가 지배적.
3. **부하 분리 효과가 핵심**: 자동완성을 Redis로 분리하면 메인검색 p95가 20ms → 6ms로 3.2배 개선. 자동완성 트래픽이 ES를 압박하지 않기 때문. 이것이 Redis 자동완성의 진짜 가치다.
4. **warm-up 전략**: 판매량 기반 상위 1,058건만 적재해도 대부분의 자동완성 커버. warm-up 2.5초.

---

## Phase 5: 검색 결과 캐싱 (Redis Cache-Aside)

### 구성
- Redis Cache-Aside: key=`search:{query}:{page}:{size}`, TTL=5분
- Micrometer 커스텀 메트릭: search.cache.hit / search.cache.miss
- Zipf 분포 쿼리셋 (인기 60% + 일반 30% + 롱테일 10%, 총 33종)

### 500VU 부하 비교

| 지표 | ES (캐시 없음) | Cached ES | 개선 |
|------|--------------|-----------|------|
| avg | 3.78ms | **2.36ms** | 1.6x |
| p50 | 3.05ms | **1.82ms** | 1.7x |
| p95 | 8.21ms | **5.88ms** | 1.4x |
| QPS | 1,922 | **3,324** | **1.7x** |
| 에러율 | 0% | 0% | - |

### 캐시 적중률

| 테스트 | 유니크 쿼리 수 | hits | misses | 적중률 |
|--------|-------------|------|--------|-------|
| 33종 쿼리셋 | 33 | 199,729 | 33 | 99.98% |
| 250종 쿼리셋 | ~250 | 355,269 | 189 | 99.95% |

### 적중률에 대한 솔직한 평가

두 테스트 모두 적중률 99.9%+ 이지만, 이건 **고정 쿼리셋을 반복하는 부하 테스트의 특성** 때문이다. 유니크 쿼리가 250종이면 초기 250건만 miss 후 나머지는 전부 hit이 된다.

실제 이커머스 검색에서는 사용자가 입력하는 검색어가 끊임없이 달라진다. 유니크 검색어 비율이 훨씬 높으므로 적중률은 이보다 낮을 것이다. 다만 Zipf 분포(상위 20% 검색어가 80% 트래픽)가 실제 패턴에 가깝다면, 인기 검색어 위주의 캐시는 효과가 있다.

### 핵심 인사이트

1. **QPS 1.7배 증가가 캐시의 핵심 가치다.** latency 개선(3ms→1.8ms)은 미미하지만, 캐시 hit 시 ES 요청을 생략하여 ES 부하를 줄이고 처리량을 높인다.
2. **적중률 99.9%는 테스트 환경의 산물이다.** 고정 쿼리셋 반복이므로 초기 miss 이후 전부 hit. 실제 서비스에서는 유니크 검색어 비율에 따라 달라진다.
3. **ES가 이미 p50 5ms인 상황에서 캐시는 "속도"보다 "부하 분산"을 위한 것이다.** ES 장애 시에도 TTL 내 캐시가 서빙할 수 있다는 점도 운영 관점에서 가치가 있다.

---

## Phase 6: Function Score 랭킹

### BM25 vs Function Score Top 5 비교 ("갤럭시" 검색)

| 순위 | BM25 (salesCount) | Function Score (salesCount) |
|------|-------------------|----------------------------|
| 1 | 32,917 | **60,150** |
| 2 | 6,372 | **45,685** |
| 3 | 45,680 | **42,586** |
| 4 | 3,162 | **93,067** |
| 5 | 9,774 | **89,275** |

### 핵심 인사이트
- BM25는 텍스트 관련도만으로 정렬 → 판매량 무시, 무작위에 가까운 순서
- Function Score는 BM25 × log1p(salesCount) × promoted(1.5x) → 인기 상품이 상위로

---

## Phase 7: 종합 벤치마크 (최종)

### 500VU, 60초, Cached ES (최종 형태)

| 지표 | 값 |
|------|-----|
| avg | 2.03ms |
| p50 | **1.57ms** |
| p90 | 3.75ms |
| p95 | **4.93ms** |
| max | 61.99ms |
| QPS | **3,175** |
| 에러율 | **0.00%** |
| 총 요청 | 190,713 |

---

## Phase 비교표 (최종)

| 지표 | Phase 1 LIKE | Phase 2 ES | Phase 5 Cached | Phase 7 최종 | 개선 (LIKE→최종) |
|------|-------------|-----------|---------------|-------------|-----------------|
| p50 | 3,010ms | 5.12ms | 1.82ms | **1.57ms** | **1,917x** |
| p95 | 3,580ms | 20.64ms | 5.88ms | **4.93ms** | **726x** |
| QPS | ~50 | 1,850 | 3,324 | **3,175** | **63x** |
| 에러율 | 17.43% | 0% | 0% | **0%** | 에러 제거 |

### 전체 아키텍처 성능 요약

| 컴포넌트 | 핵심 수치 |
|---------|----------|
| ES 검색 (Nori) | p50=5ms, 588배 개선 |
| CDC 동기화 | 675ms lag, 삭제 감지 |
| Redis 자동완성 | 4ms (ES 대비 30배) |
| Redis 캐싱 | 적중률 99.98% |
| Function Score | 인기 상품 상위 랭킹 |
| **최종 (500VU)** | **p50=1.57ms, QPS 3,175, 0% 에러** |
