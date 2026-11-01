// 시·도 중심 좌표와 가중치(합계 100), 흩뿌리는 표준편차(도)다. 검증용 데이터 생성 스크립트(seed/seed.sql)와 같은 값을 쓴다.
// 그래서 지도 조회 스크립트가 고르는 화면 영역도 장소가 몰린 곳에 더 자주 떨어진다. 한쪽을 바꾸면 다른 쪽도 같이 고친다.
export const REGIONS = [
    { name: '경기', lat: 37.55, lng: 127.25, sigma: 0.35, weight: 22 },
    { name: '강원', lat: 37.75, lng: 128.3, sigma: 0.35, weight: 17 },
    { name: '경북', lat: 36.35, lng: 128.75, sigma: 0.35, weight: 9 },
    { name: '경남', lat: 35.35, lng: 128.25, sigma: 0.35, weight: 8 },
    { name: '충남', lat: 36.55, lng: 126.85, sigma: 0.35, weight: 7 },
    { name: '충북', lat: 36.75, lng: 127.75, sigma: 0.35, weight: 6 },
    { name: '전남', lat: 34.85, lng: 126.95, sigma: 0.35, weight: 6 },
    { name: '전북', lat: 35.72, lng: 127.15, sigma: 0.35, weight: 5 },
    { name: '인천', lat: 37.46, lng: 126.44, sigma: 0.08, weight: 4 },
    { name: '제주', lat: 33.38, lng: 126.55, sigma: 0.15, weight: 3 },
    { name: '서울', lat: 37.56, lng: 126.98, sigma: 0.08, weight: 2 },
    { name: '부산', lat: 35.18, lng: 129.08, sigma: 0.08, weight: 2 },
    { name: '대구', lat: 35.87, lng: 128.6, sigma: 0.08, weight: 2 },
    { name: '대전', lat: 36.35, lng: 127.38, sigma: 0.08, weight: 2 },
    { name: '광주', lat: 35.16, lng: 126.85, sigma: 0.08, weight: 2 },
    { name: '울산', lat: 35.54, lng: 129.31, sigma: 0.08, weight: 2 },
    { name: '세종', lat: 36.48, lng: 127.29, sigma: 0.08, weight: 1 },
];

export function pickWeighted(items) {
    const total = items.reduce((sum, item) => sum + item.weight, 0);
    let roll = Math.random() * total;
    for (const item of items) {
        roll -= item.weight;
        if (roll < 0) {
            return item;
        }
    }
    return items[items.length - 1];
}

// 호출하면 가중치로 시·도를 고르고, 그 중심에서 정규분포(박스-뮬러 변환)로 흩뿌린 좌표를 돌려준다.
export function randomPointNearRegion() {
    const region = pickWeighted(REGIONS);
    const u1 = 1 - Math.random();
    const u2 = Math.random();
    const radius = Math.sqrt(-2 * Math.log(u1));
    return {
        lat: region.lat + region.sigma * radius * Math.cos(2 * Math.PI * u2),
        lng: region.lng + region.sigma * radius * Math.sin(2 * Math.PI * u2),
    };
}
