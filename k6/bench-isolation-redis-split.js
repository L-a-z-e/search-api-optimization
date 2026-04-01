import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const mainSearchP95 = new Trend('main_search_duration');
const autoP95 = new Trend('auto_duration');

const POPULAR = ['삼성','갤럭시','아이폰','나이키','아디다스','맥북','노트북','에어맥스','비비고','라면','운동화','크림','다이슨','애플','LG','뉴발란스','설화수','코트','이니스프리','청바지'];
const PREFIXES = ['삼','갤','아이','나이','맥','노','에어','비','라','운','크','다','애','엘','뉴','설','코','이니','청'];

function query() {
    return POPULAR[Math.floor(Math.random() * POPULAR.length)];
}
function prefix() {
    return PREFIXES[Math.floor(Math.random() * PREFIXES.length)];
}

export const options = {
    scenarios: {
        autocomplete: {
            executor: 'ramping-vus',
            exec: 'autoRedis',
            stages: [
                { duration: '10s', target: 100 },
                { duration: '10s', target: 300 },
                { duration: '60s', target: 300 },
            ],
        },
        main_search: {
            executor: 'ramping-vus',
            exec: 'mainSearch',
            stages: [
                { duration: '10s', target: 50 },
                { duration: '10s', target: 200 },
                { duration: '60s', target: 200 },
            ],
        },
    },
};

export function autoRedis() {
    const res = http.get(`http://localhost:8080/api/suggest/redis?q=${encodeURIComponent(prefix())}&limit=10`, { timeout: '10s' });
    autoP95.add(res.timings.duration);
    check(res, { 'auto_ok': (r) => r.status === 200 });
    sleep(0.05);
}

export function mainSearch() {
    const res = http.get(`http://localhost:8080/api/search/es?q=${encodeURIComponent(query())}&size=20`, { timeout: '10s' });
    mainSearchP95.add(res.timings.duration);
    check(res, { 'search_ok': (r) => r.status === 200 });
    sleep(0.1);
}
