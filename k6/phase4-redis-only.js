import http from 'k6/http';
import { check, sleep } from 'k6';
const P = ['삼','삼성','갤','갤럭','나이','나이키','애플','아이','맥','노트','운동','크림','다이','LG','뉴발','설화','코트','라면','비비','청바'];
export const options = { scenarios: { redis: { executor: 'ramping-vus', stages: [{ duration: '10s', target: 100 },{ duration: '10s', target: 200 },{ duration: '20s', target: 300 }] } } };
export default function() {
    const res = http.get(`http://localhost:8080/api/suggest/redis?q=${encodeURIComponent(P[Math.floor(Math.random()*P.length)])}&limit=10`, { timeout: '10s' });
    check(res, { 'ok': (r) => r.status === 200 });
    sleep(0.05);
}
