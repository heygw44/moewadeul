// 지도 영역 조회 부하 검증. 장소 100만 건이 들어간 환경에서 동시 사용자 300명이 지도를 움직이는 상황을 흉내 낸다.
// 기준: 지도 영역 조회 p95 200ms 이하, 오류율 1% 미만.
//
// 환경 변수: BASE_URL(기본 http://localhost:8080), VUS(기본 300), DURATION(기본 10m)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib/auth.js';
import { pickWeighted, randomPointNearRegion } from './lib/regions.js';

const VUS = Number(__ENV.VUS || 300);
const DURATION = __ENV.DURATION || '10m';
const MAX_ITEMS = 500;

// 카카오맵 확대 수준별 화면 비율. 사람은 전국 지도를 잠깐 보고 시·군이나 동네 단위로 들어가서 오래 본다고 가정했다.
// 화면 너비(경도)는 13레벨에서 남한 전체가 들어가는 7도로 잡고, 한 레벨 내려갈 때마다 절반이 된다. 높이(위도)는 너비의 0.8배다.
const ZOOM_LEVELS = [
    { level: 13, weight: 10 },
    { level: 10, weight: 30 },
    { level: 8, weight: 40 },
    { level: 5, weight: 20 },
];
const NATIONWIDE_LEVEL = 13;
const NATIONWIDE_LNG_SPAN = 7.0;
const LAT_SPAN_RATIO = 0.8;
const KOREA_CENTER = { lat: 36.3, lng: 127.8 };

export const options = {
    scenarios: {
        map: { executor: 'constant-vus', vus: VUS, duration: DURATION },
    },
    thresholds: {
        'http_req_duration{name:spots-area}': ['p(95)<200'],
        'http_req_failed{name:spots-area}': ['rate<0.01'],
        // 아래 네 줄은 판정용이 아니다. k6 요약에 확대 수준별 응답 시간을 따로 보이게 하려고 늘 통과하는 조건을 걸었다.
        'http_req_duration{name:spots-area,level:13}': ['max>=0'],
        'http_req_duration{name:spots-area,level:10}': ['max>=0'],
        'http_req_duration{name:spots-area,level:8}': ['max>=0'],
        'http_req_duration{name:spots-area,level:5}': ['max>=0'],
    },
};

function randomViewport() {
    const { level } = pickWeighted(ZOOM_LEVELS);
    const lngSpan = NATIONWIDE_LNG_SPAN / 2 ** (NATIONWIDE_LEVEL - level);
    const latSpan = lngSpan * LAT_SPAN_RATIO;
    const center = level === NATIONWIDE_LEVEL ? KOREA_CENTER : randomPointNearRegion();
    return {
        level,
        swLat: (center.lat - latSpan / 2).toFixed(6),
        swLng: (center.lng - lngSpan / 2).toFixed(6),
        neLat: (center.lat + latSpan / 2).toFixed(6),
        neLng: (center.lng + lngSpan / 2).toFixed(6),
    };
}

export default function () {
    const v = randomViewport();
    const url =
        `${BASE_URL}/api/spots?swLat=${v.swLat}&swLng=${v.swLng}&neLat=${v.neLat}&neLng=${v.neLng}` +
        `&zoom=${v.level}`;
    const res = http.get(url, { tags: { name: 'spots-area', level: String(v.level) } });
    check(res, {
        '200 응답': (r) => r.status === 200,
        '마커와 묶음 합이 500개 이하': (r) => {
            if (r.status !== 200) {
                return false;
            }
            const body = r.json();
            return body.markers.length + body.clusters.length <= MAX_ITEMS;
        },
    });
    // 사람이 지도를 옮기고 화면을 보는 시간. 1~3초 사이에서 무작위로 쉰다.
    sleep(1 + Math.random() * 2);
}
