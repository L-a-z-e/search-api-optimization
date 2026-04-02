import http from 'k6/http';
import { check, sleep } from 'k6';
import { vu } from 'k6/execution';

// 인기 검색어 50종 (전체 트래픽의 50%)
const POPULAR = [
    '삼성','갤럭시','아이폰','나이키','아디다스','맥북','노트북','에어맥스','비비고','라면',
    '운동화','크림','다이슨','애플','LG','뉴발란스','설화수','코트','이니스프리','청바지',
    '냉장고','세탁기','에어컨','TV','모니터','키보드','마우스','이어폰','스피커','카메라',
    '선크림','샴푸','치약','커피','소파','침대','책상','의자','가방','시계',
    '반지','목걸이','선글라스','모자','양말','티셔츠','원피스','자켓','패딩','부츠'
];

// 일반 검색어 200종 (전체 트래픽의 30%)
const NORMAL = [
    '블루투스 이어폰','여름 원피스','남성 운동화','무선 청소기','즉석밥','스마트워치','요가매트','볼캡',
    '겨울 패딩','여성 코트','캠핑 텐트','러닝화 추천','유아 장난감','강아지 사료','고양이 간식',
    '전기 자전거','무선 키보드','게이밍 마우스','노이즈캔슬링','공기청정기','제습기 추천','로봇 청소기',
    '전기밥솥','에어프라이어','블렌더 추천','커피머신','캡슐 커피','탄산수 제조기','정수기 필터',
    '아기 분유','기저귀 추천','유모차 브랜드','임산부 영양제','어린이 비타민','초등 학습지',
    '남성 면도기','전동 칫솔','헤어 드라이어','고데기 추천','네일 세트','립스틱 컬러',
    '골프 클럽','등산 배낭','수영복 여성','자전거 헬멧','요가 레깅스','테니스 라켓',
    '와인 추천','위스키 입문','맥주 세트','전통주 선물','홈카페 용품','텀블러 보온',
    '강아지 하네스','고양이 화장실','수족관 세트','소형견 옷','펫 카시트','앵무새 먹이',
    '아이패드 케이스','갤럭시 필름','맥북 파우치','에어팟 케이스','충전기 멀티','USB 허브',
    '디퓨저 향','캔들 세트','가습기 필터','공기청정기 필터','에어컨 필터','먼지 제거',
    '차량용 방향제','블랙박스 추천','네비게이션','타이어 교체','와이퍼 사이즈','엔진오일',
    '도서 베스트','전자책 단말기','학습 플래너','다이어리 추천','만년필 입문','형광펜 세트',
    '주방 수납','욕실 선반','현관 매트','커튼 레일','LED 조명','스탠드 조명',
    '여행 캐리어','여권 케이스','목베개 추천','멀티어댑터','보조배터리','셀카봉',
    '토익 교재','영어회화 앱','일본어 독학','중국어 입문','프로그래밍 책','코딩 강의',
    '프로틴 파우더','다이어트 보조제','관절 영양제','눈 건강 영양제','유산균 추천','콜라겐',
    '면 마스크','핸드크림','바디로션','선스틱','토너 패드','클렌징 폼',
    '미니 냉장고','원룸 세탁기','자취 필수템','전자레인지','토스터 오븐','인덕션',
    '캠핑 의자','그릴 세트','아이스박스','랜턴 충전식','침낭 사계절','타프 텐트',
    '강아지 산책줄','고양이 캣타워','자동 급식기','반려동물 보험','펫 유모차','강아지 옷 겨울',
    '유아 카시트','아기 보행기','젖병 소독기','이유식 재료','유아 식판','어린이 치약',
    '스마트 체중계','안마기 목','족욕기','혈압계','체온계','산소포화도',
    '무선 충전기','맥세이프','고속 충전기','노트북 거치대','모니터 암','데스크 매트'
];

// 롱테일: 실제 키워드 조합으로 ES 매칭 결과는 있되, VU+iteration으로 캐시 키는 100% 유니크

const BRANDS = ['삼성','LG','애플','소니','나이키','아디다스','다이슨','필립스','보쉬','이케아'];
const PRODUCTS = ['갤럭시','에어맥스','맥북','냉장고','세탁기','청소기','이어폰','운동화','크림','세럼'];
const COLORS = ['블랙','화이트','그레이','네이비','레드','블루','실버','골드','핑크','베이지'];
const MODIFIERS = ['프리미엄','울트라','프로','미니','슬림','클래식','에센셜','리미티드','스페셜','베이직'];

function generateLongtail() {
    const brand = BRANDS[Math.floor(Math.random() * BRANDS.length)];
    const product = PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)];
    const color = COLORS[Math.floor(Math.random() * COLORS.length)];
    const mod = MODIFIERS[Math.floor(Math.random() * MODIFIERS.length)];
    // ES는 앞 키워드로 매칭(결과 있음), 캐시 키는 뒤 숫자로 100% 유니크(반드시 MISS)
    return `${brand} ${product} ${color} ${mod} ${vu.idInTest}-${vu.iterationInScenario}`;
}

// 실제 이커머스 검색어 분포:
// - 인기(상위 50종)가 70% 트래픽
// - 일반(200종)이 25%
// - 완전 유니크 롱테일이 5% (매번 새로운 검색어, 반드시 MISS)
function query() {
    const r = Math.random();
    if (r < 0.70) return POPULAR[Math.floor(Math.random() * POPULAR.length)];
    if (r < 0.95) return NORMAL[Math.floor(Math.random() * NORMAL.length)];
    return generateLongtail();  // 실제 키워드+VU ID로 ES 매칭 O, 캐시 100% MISS
}

export const options = {
    scenarios: {
        multilayer_cache: {
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

// __ENV.ENDPOINT로 테스트 대상을 전환
// k6 run -e ENDPOINT=es k6/bench-multilayer-realistic.js         → 캐시 없음
// k6 run -e ENDPOINT=cached k6/bench-multilayer-realistic.js     → Redis 단일
// k6 run -e ENDPOINT=multilayer k6/bench-multilayer-realistic.js → L1+L2
const ENDPOINT = __ENV.ENDPOINT || 'multilayer';

export default function () {
    const q = query();
    const url = `http://localhost:8080/api/search/${ENDPOINT}?q=${encodeURIComponent(q)}&size=20`;
    const res = http.get(url, { timeout: '30s' });
    check(res, { 'ok': (r) => r.status === 200 });
    sleep(0.1);
}
