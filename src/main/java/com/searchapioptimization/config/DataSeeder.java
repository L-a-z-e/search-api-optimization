package com.searchapioptimization.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    private static final int TOTAL_PRODUCTS = 1_000_000;
    private static final int BATCH_SIZE = 5_000;

    private static final String[] CATEGORIES = {
            "전자기기", "패션의류", "식품", "뷰티", "스포츠", "가구인테리어",
            "도서", "완구", "자동차용품", "반려동물", "건강식품", "유아동",
            "주방용품", "문구사무", "악기", "여행", "디지털", "생활용품",
            "원예", "공구"
    };

    private static final String[] BRANDS = {
            "삼성", "삼성전자", "LG", "LG전자", "애플", "소니", "나이키", "아디다스",
            "뉴발란스", "푸마", "노스페이스", "파타고니아", "유니클로", "자라",
            "CJ", "오뚜기", "풀무원", "농심", "삼양", "비비고",
            "이니스프리", "설화수", "라네즈", "헤라", "미샤", "더페이스샵",
            "다이슨", "필립스", "보쉬", "밀레", "쿠쿠", "코웨이",
            "이케아", "한샘", "시몬스", "에이스", "일룸",
            "아모레퍼시픽", "로레알", "에스티로더", "샤넬", "디올",
            "언더아머", "데상트", "MLB", "휠라", "리복",
            "레노버", "델", "HP", "에이수스", "MSI",
            "캐논", "니콘", "후지필름", "올림푸스", "라이카"
    };

    private static final String[][] PRODUCT_TEMPLATES = {
            // 전자기기
            {"삼성 갤럭시 S24 울트라 256GB", "삼성 갤럭시 Z폴드6 512GB", "삼성 갤럭시 버즈3 프로",
                    "LG 그램 17인치 노트북", "애플 아이폰 16 프로맥스", "애플 맥북 프로 M4",
                    "소니 WH-1000XM5 헤드폰", "다이슨 V15 무선청소기", "삼성 비스포크 냉장고",
                    "LG 올레드 TV 65인치", "애플 아이패드 프로 12.9", "삼성 갤럭시 탭 S9"},
            // 패션의류
            {"나이키 에어맥스 97 블랙", "아디다스 울트라부스트 런닝화", "뉴발란스 993 그레이",
                    "노스페이스 눕시 패딩 블랙", "유니클로 히트텍 이너웨어", "자라 오버사이즈 코트",
                    "나이키 드라이핏 반팔 티셔츠", "파타고니아 플리스 자켓", "푸마 RS-X 스니커즈"},
            // 식품
            {"CJ 비비고 왕교자 만두 1.05kg", "오뚜기 진라면 멀티팩 5입", "농심 신라면 블랙 사발면",
                    "풀무원 두부 300g", "삼양 불닭볶음면 5입", "CJ 햇반 즉석밥 210g",
                    "비비고 갈비탕 500g", "오뚜기 카레 약간매운맛", "농심 새우깡 90g"},
            // 뷰티
            {"이니스프리 그린티 세럼", "설화수 윤조에센스 90ml", "라네즈 워터뱅크 크림",
                    "헤라 블랙쿠션 SPF34", "미샤 BB크림 50ml", "더페이스샵 클렌징오일",
                    "아모레퍼시픽 타임레스폰스 크림", "로레알 리바이탈리프트 세럼"},
            // 스포츠
            {"나이키 줌 페가수스 40 런닝화", "언더아머 UA 테크 반팔티", "데상트 트레이닝 팬츠",
                    "MLB 뉴욕양키스 볼캡", "휠라 디스럽터2 운동화", "리복 클래식 레더 화이트"},
    };

    @Override
    public void run(String... args) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
        if (count != null && count >= TOTAL_PRODUCTS) {
            log.info("이미 {}건 데이터 존재. Seed 스킵.", count);
            return;
        }

        log.info("=== 상품 데이터 {}건 Seed 시작 ===", TOTAL_PRODUCTS);
        long startTime = System.currentTimeMillis();

        // FULLTEXT 인덱스 임시 비활성화 (인덱싱 속도 향상)
        jdbcTemplate.execute("ALTER TABLE product DISABLE KEYS");

        Random random = new Random(42); // 재현 가능한 시드
        int inserted = 0;

        while (inserted < TOTAL_PRODUCTS) {
            int batchCount = Math.min(BATCH_SIZE, TOTAL_PRODUCTS - inserted);
            List<Object[]> batchArgs = new ArrayList<>(batchCount);

            for (int i = 0; i < batchCount; i++) {
                batchArgs.add(generateProduct(random));
            }

            jdbcTemplate.batchUpdate(
                    "INSERT INTO product (name, brand, category, price, sales_count, promoted, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    batchArgs
            );

            inserted += batchCount;
            if (inserted % 100_000 == 0) {
                log.info("Seed 진행: {}/{}건 ({:.1f}%)", inserted, TOTAL_PRODUCTS,
                        (double) inserted / TOTAL_PRODUCTS * 100);
            }
        }

        // FULLTEXT 인덱스 재활성화
        log.info("FULLTEXT 인덱스 재구축 중...");
        jdbcTemplate.execute("ALTER TABLE product ENABLE KEYS");

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("=== Seed 완료: {}건, {}초 ===", TOTAL_PRODUCTS, elapsed / 1000);
    }

    private Object[] generateProduct(Random random) {
        String category = CATEGORIES[random.nextInt(CATEGORIES.length)];
        String brand = BRANDS[random.nextInt(BRANDS.length)];

        // 상품명 생성: 템플릿 기반 + 변형
        String name = generateProductName(random, brand, category);

        // 가격: 1,000 ~ 5,000,000 (로그 분포)
        long price = generatePrice(random);

        // 판매량: Zipf 분포 (소수 상품이 많이 팔림)
        long salesCount = generateZipfSalesCount(random);

        // 프로모션: 10% 확률
        boolean promoted = random.nextDouble() < 0.1;

        // 등록일: 최근 2년 내
        LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(730));
        LocalDateTime updatedAt = createdAt.plusDays(random.nextInt(30));

        return new Object[]{name, brand, category, price, salesCount, promoted, createdAt, updatedAt};
    }

    private String generateProductName(Random random, String brand, String category) {
        // 50%: 템플릿 기반, 50%: 조합 생성
        if (random.nextDouble() < 0.5) {
            String[] allTemplates = Arrays.stream(PRODUCT_TEMPLATES)
                    .flatMap(Arrays::stream)
                    .toArray(String[]::new);
            String template = allTemplates[random.nextInt(allTemplates.length)];
            // 색상/사이즈 변형 추가
            return template + " " + randomVariant(random);
        }

        // 조합 생성
        String[] adjectives = {"프리미엄", "에센셜", "클래식", "프로", "울트라", "슬림", "베이직", "리미티드"};
        String[] types = getTypesForCategory(category);
        String[] colors = {"블랙", "화이트", "그레이", "네이비", "베이지", "레드", "블루", "그린"};
        String[] sizes = {"S", "M", "L", "XL", "FREE", "250mm", "260mm", "270mm"};

        StringBuilder sb = new StringBuilder();
        sb.append(brand).append(" ");
        if (random.nextDouble() < 0.3) sb.append(adjectives[random.nextInt(adjectives.length)]).append(" ");
        sb.append(types[random.nextInt(types.length)]);
        if (random.nextDouble() < 0.5) sb.append(" ").append(colors[random.nextInt(colors.length)]);
        if (random.nextDouble() < 0.3) sb.append(" ").append(sizes[random.nextInt(sizes.length)]);

        return sb.toString();
    }

    private String[] getTypesForCategory(String category) {
        return switch (category) {
            case "전자기기" -> new String[]{"스마트폰", "노트북", "태블릿", "이어폰", "헤드폰", "스마트워치", "모니터", "키보드", "마우스"};
            case "패션의류" -> new String[]{"티셔츠", "청바지", "코트", "자켓", "원피스", "운동화", "스니커즈", "부츠", "슬리퍼"};
            case "식품" -> new String[]{"라면", "과자", "음료", "즉석밥", "만두", "소스", "커피", "차"};
            case "뷰티" -> new String[]{"세럼", "크림", "토너", "선크림", "클렌저", "마스크팩", "파운데이션", "립스틱"};
            case "스포츠" -> new String[]{"런닝화", "트레이닝복", "요가매트", "덤벨", "축구공", "배드민턴", "수영복"};
            default -> new String[]{"상품A", "상품B", "상품C", "세트", "패키지", "기획상품"};
        };
    }

    private String randomVariant(Random random) {
        String[] variants = {"", "블랙", "화이트", "실버", "골드", "2024", "2025", "한정판", "에디션"};
        return variants[random.nextInt(variants.length)];
    }

    private long generatePrice(Random random) {
        // 로그 분포: 대부분 저가, 일부 고가
        double logPrice = 3 + random.nextDouble() * 3.7; // 10^3 ~ 10^6.7
        long price = (long) Math.pow(10, logPrice);
        return (price / 100) * 100; // 100원 단위 절사
    }

    private long generateZipfSalesCount(Random random) {
        // Zipf 분포: 소수가 매우 높은 판매량
        double u = random.nextDouble();
        if (u < 0.01) return ThreadLocalRandom.current().nextLong(50000, 100001);
        if (u < 0.05) return ThreadLocalRandom.current().nextLong(10000, 50001);
        if (u < 0.15) return ThreadLocalRandom.current().nextLong(1000, 10001);
        if (u < 0.40) return ThreadLocalRandom.current().nextLong(100, 1001);
        return ThreadLocalRandom.current().nextLong(0, 101);
    }
}
