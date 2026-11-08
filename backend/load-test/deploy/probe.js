// #655 틀: 서버 밖에서 Caddy(80)로 GET /api/programs 초당 2건, 로그인한 회원이 자기 글에 댓글 POST 초당 2건.
// 응답 코드가 200·201이 아니면 실패다. 요청마다 보낸 시각(ms)·종류·코드·소요 시간을 console.log로 남긴다.
// 요청 제한은 Caddy의 lb_try_duration(30초)보다 길게 40초로 둬서, Caddy가 붙잡고 기다린 요청도 결과까지 본다.
import http from 'k6/http';
import exec from 'k6/execution';
import { fetchCsrfToken, login, authHeaders } from '../lib/auth.js';

const BASE = (__ENV.BASE_URL || 'http://127.0.0.1').replace(/\/$/, '');
const DURATION = __ENV.DURATION || '900s';
const RUN = __ENV.RUN_NAME || 'run';

export const options = {
    scenarios: {
        get: { executor: 'constant-arrival-rate', exec: 'getPrograms', rate: 2, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 20, maxVUs: 200 },
        post: { executor: 'constant-arrival-rate', exec: 'postComment', rate: 2, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 20, maxVUs: 200 },
    },
    discardResponseBodies: true,
};

export function setup() {
    const csrf = fetchCsrfToken();
    const session = login(__ENV.COMMENT_EMAIL, csrf);
    console.log('SETUP_DONE');
    return { csrf, session };
}

export function getPrograms() {
    const start = Date.now();
    const res = http.get(`${BASE}/api/programs`, { timeout: '40s' });
    console.log(`R ${start} GET ${res.status} ${res.timings.duration.toFixed(1)} ${res.error_code || 0}`);
}

export function postComment(data) {
    const iter = exec.scenario.iterationInTest;
    const start = Date.now();
    const res = http.post(
        `${BASE}/api/community/posts/${__ENV.COMMENT_POST_ID}/comments`,
        JSON.stringify({ content: `배포 측정 ${RUN} ${iter}` }),
        {
            headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `dm-${RUN}-${iter}`, ...authHeaders(data.session, data.csrf) },
            timeout: '40s',
        },
    );
    console.log(`R ${start} POST ${res.status} ${res.timings.duration.toFixed(1)} ${res.error_code || 0} ${iter}`);
}
