// 반경 검색 부하 측정. 장소 100만 건이 들어간 환경에서 가상 사용자가 시·도 가중치로 고른 중심에서 반경 검색을 반복한다.
// 반경 검색에는 수치 기준이 없다. 그래서 판정 조건을 두지 않고, 반경별 응답 시간을 요약에 따로 보이게만 한다.
//
// 환경 변수: BASE_URL(기본 http://localhost:8080), VUS(기본 10), DURATION(기본 2m),
//            RADII(기본 5,10,20,50. 쉼표로 나눈 km 목록에서 같은 비율로 고른다)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib/auth.js';
import { pickWeighted, randomPointNearRegion } from './lib/regions.js';

const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '2m';
const RADII = (__ENV.RADII || '5,10,20,50').split(',').map((km) => Number(km.trim()));
const PAGE_SIZE = 20;

// 조건 비율. 화면에서 반경 검색은 AI 검색이 문장의 거리를 읽었을 때 열리고, 이때 시설·휴장 조건이 함께 붙을 수 있다고 가정했다.
const FILTERS = [
    { name: 'none', query: '', weight: 60 },
    { name: 'water', query: '&hasWater=true', weight: 20 },
    { name: 'hideClosed', query: '&hideClosed=true', weight: 20 },
];

const radiusThresholds = Object.fromEntries(
    RADII.map((km) => [`http_req_duration{name:spots-nearby,radius:${km}}`, ['max>=0']]),
);

export const options = {
    scenarios: {
        nearby: { executor: 'constant-vus', vus: VUS, duration: DURATION },
    },
    // 판정용이 아니다. k6 요약에 반경별 응답 시간을 따로 보이게 하려고 늘 통과하는 조건을 걸었다.
    thresholds: {
        'http_req_duration{name:spots-nearby}': ['max>=0'],
        ...radiusThresholds,
    },
};

export default function () {
    const center = randomPointNearRegion();
    const radiusKm = RADII[Math.floor(Math.random() * RADII.length)];
    const filter = pickWeighted(FILTERS);
    // 사람은 대부분 첫 페이지만 본다고 가정했다. 열 번에 한 번만 2~5쪽을 넘긴다.
    const page = Math.random() < 0.1 ? 1 + Math.floor(Math.random() * 4) : 0;
    const url =
        `${BASE_URL}/api/spots/nearby?lat=${center.lat.toFixed(6)}&lng=${center.lng.toFixed(6)}` +
        `&radiusKm=${radiusKm}&page=${page}&size=${PAGE_SIZE}${filter.query}`;
    const res = http.get(url, {
        tags: { name: 'spots-nearby', radius: String(radiusKm), filter: filter.name },
    });
    check(res, {
        '200 응답': (r) => r.status === 200,
        '한 페이지 크기 이하': (r) => r.status === 200 && r.json().content.length <= PAGE_SIZE,
    });
    // 사람이 결과 목록을 보는 시간. 1~3초 사이에서 무작위로 쉰다.
    sleep(1 + Math.random() * 2);
}
