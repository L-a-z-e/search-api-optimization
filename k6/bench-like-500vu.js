import http from 'k6/http';
import { check, sleep } from 'k6';

const POPULAR = ['삼성','갤럭시','아이폰','나이키','아디다스','맥북','노트북','에어맥스','비비고','라면','운동화','크림','다이슨','애플','LG','뉴발란스','설화수','코트','이니스프리','청바지'];
const NORMAL = ['블루투스 이어폰','여름 원피스','남성 운동화','무선 청소기','즉석밥','스마트워치','선크림','요가매트','볼캡','키보드'];
const LONGTAIL = ['삼성 갤럭시 S24 울트라 256GB','CJ 비비고 왕교자 만두','나이키 에어맥스 97 블랙'];

function q() {
    const r = Math.random();
    if (r < 0.6) return POPULAR[Math.floor(Math.random() * POPULAR.length)];
    if (r < 0.9) return NORMAL[Math.floor(Math.random() * NORMAL.length)];
    return LONGTAIL[Math.floor(Math.random() * LONGTAIL.length)];
}

export const options = {
    scenarios: {
        like_500vu: {
            executor: 'ramping-vus',
            stages: [
                { duration: '10s', target: 100 },
                { duration: '10s', target: 300 },
                { duration: '10s', target: 500 },
                { duration: '60s', target: 500 },
            ],
        },
    },
};

export default function () {
    const res = http.get(`http://localhost:8080/api/search/like?q=${encodeURIComponent(q())}&size=20`, { timeout: '30s' });
    check(res, { 'ok': (r) => r.status === 200 });
    sleep(0.1);
}
