import http from 'k6/http';
import { check, sleep } from 'k6';

const POPULAR_QUERIES = [
    '삼성', '갤럭시', '아이폰', '나이키', '아디다스', '맥북', '노트북',
    '에어맥스', '이니스프리', '비비고', '라면', '운동화', '청바지', '크림',
    '다이슨', '애플', 'LG', '뉴발란스', '설화수', '코트'
];
const NORMAL_QUERIES = [
    '블루투스 이어폰', '여름 원피스', '남성 운동화', '무선 청소기', '즉석밥',
    '프리미엄 헤드폰', '스마트워치', '선크림', '요가매트', '볼캡'
];
const LONGTAIL_QUERIES = [
    '삼성 갤럭시 S24 울트라 256GB',
    'CJ 비비고 왕교자 만두 1.05kg',
    '나이키 에어맥스 97 남성 블랙'
];

function getQuery() {
    const r = Math.random();
    if (r < 0.6) return POPULAR_QUERIES[Math.floor(Math.random() * POPULAR_QUERIES.length)];
    if (r < 0.9) return NORMAL_QUERIES[Math.floor(Math.random() * NORMAL_QUERIES.length)];
    return LONGTAIL_QUERIES[Math.floor(Math.random() * LONGTAIL_QUERIES.length)];
}

export const options = {
    scenarios: {
        like_search: {
            executor: 'ramping-vus',
            stages: [
                { duration: '10s', target: 50 },
                { duration: '10s', target: 100 },
                { duration: '10s', target: 200 },
                { duration: '30s', target: 200 },
            ],
            exec: 'likeSearch',
        },
        fulltext_search: {
            executor: 'ramping-vus',
            stages: [
                { duration: '10s', target: 50 },
                { duration: '10s', target: 100 },
                { duration: '10s', target: 200 },
                { duration: '30s', target: 200 },
            ],
            exec: 'fulltextSearch',
            startTime: '70s',
        },
    },
};

export function likeSearch() {
    const query = encodeURIComponent(getQuery());
    const res = http.get(`http://localhost:8080/api/search/like?q=${query}&size=20`, {
        timeout: '30s',
    });
    check(res, {
        'status is 200': (r) => r.status === 200,
    });
    sleep(0.1);
}

export function fulltextSearch() {
    const query = encodeURIComponent(getQuery());
    const res = http.get(`http://localhost:8080/api/search/fulltext?q=${query}&size=20`, {
        timeout: '30s',
    });
    check(res, {
        'status is 200': (r) => r.status === 200,
    });
    sleep(0.1);
}
