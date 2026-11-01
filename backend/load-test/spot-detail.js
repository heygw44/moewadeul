// 장소 상세 부하 측정. 지도에서 장소를 누르는 상황을 흉내 내어, 가상 사용자가 장소 ID를 무작위로 골라 상세를 반복 조회한다.
// 장소 상세에는 수치 기준이 없다. 그래서 판정 조건을 두지 않고 응답 시간만 요약에 남긴다.
// 날씨는 장소 상세와 따로 조회하고 외부 API를 부르므로 이 스크립트는 날씨를 부르지 않는다.
//
// 환경 변수: BASE_URL(기본 http://localhost:8080), VUS(기본 300), DURATION(기본 2m),
//            SPOT_MAX_ID(기본 1000000. 검증 DB의 SELECT MAX(id) FROM spot 값을 넣는다)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL } from './lib/auth.js';

const VUS = Number(__ENV.VUS || 300);
const DURATION = __ENV.DURATION || '2m';
const SPOT_MAX_ID = Number(__ENV.SPOT_MAX_ID || 1000000);

// 404는 장소를 읽지 않고 끝나서 200보다 빠르다. 그래서 200 응답의 시간만 따로 모은다.
const found = new Trend('spot_detail_found_duration', true);

export const options = {
    scenarios: {
        detail: { executor: 'constant-vus', vus: VUS, duration: DURATION },
    },
    // 판정용이 아니다. 없는 ID와 숨김·검토 대기 장소는 404라서 실패율 대신 아래 check로 응답 코드를 본다.
    thresholds: {
        'http_req_duration{name:spot-detail}': ['max>=0'],
        spot_detail_found_duration: ['max>=0'],
    },
};

export default function () {
    // 검증용 데이터는 장소 ID 사이에 빈 번호가 있다. 빈 번호나 숨김·검토 대기 장소를 고르면 서버가 404로 응답한다.
    const spotId = 1 + Math.floor(Math.random() * SPOT_MAX_ID);
    const res = http.get(`${BASE_URL}/api/spots/${spotId}`, {
        tags: { name: 'spot-detail' },
        responseCallback: http.expectedStatuses(200, 404),
    });
    if (res.status === 200) {
        found.add(res.timings.duration);
    }
    check(res, {
        '200 또는 404 응답': (r) => r.status === 200 || r.status === 404,
    });
    // 사람이 상세를 읽는 시간. 1~3초 사이에서 무작위로 쉰다.
    sleep(1 + Math.random() * 2);
}
