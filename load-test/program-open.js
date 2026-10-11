// 행사 오픈 순간 신청 부하 검증. 정원 200석 행사에 신청이 열리자마자 2,000명이 한꺼번에 신청하는 상황을 흉내 낸다.
// 기준: 성공(201)은 정확히 정원만큼, 나머지는 모두 매진(409 PROGRAM_SOLD_OUT)이고 그 밖의 응답은 0건.
//
// 환경 변수
//   BASE_URL     기본 http://localhost:8080
//   APPLICANTS   신청 회원 수. 기본 2000. 검증용 데이터의 신청 회원 수보다 많으면 안 된다
//   CAPACITY     행사 정원. 기본 200
//   OVERNIGHT    숙박 행사 여부. 기본 true. 숙박 행사는 서버가 신청마다 신뢰 단계를 계산한다
//   OPEN_DELAY   준비가 끝난 뒤 신청이 열리기까지 기다릴 초. 기본 20
//
// 회마다 새 행사를 만들므로 같은 데이터로 여러 번 돌려도 된다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { ADMIN_EMAIL, BASE_URL, applicantEmail, authHeaders, fetchCsrfToken, login, loginRequest, sessionFrom } from './lib/auth.js';

const APPLICANTS = Number(__ENV.APPLICANTS || 2000);
const CAPACITY = Number(__ENV.CAPACITY || 200);
const OVERNIGHT = (__ENV.OVERNIGHT || 'true') === 'true';
const OPEN_DELAY_SECONDS = Number(__ENV.OPEN_DELAY || 20);

const LOGIN_BATCH_SIZE = 50;
// 서버 시각은 Date 헤더로만 알 수 있고 Date 헤더는 초 단위라서, 우리 쪽 추정이 최대 1초 이를 수 있다. 그만큼 늦게 출발한다.
const CLOCK_MARGIN_MS = 1000;
// 오픈 순간에 모든 요청이 같은 밀리초에 몰리지는 않으므로, 회원마다 0~500ms 사이에서 무작위로 늦게 보낸다.
const MAX_JITTER_MS = 500;
// 측정하는 동안 결제 만료 작업이 자리를 돌려주면 성공 수가 정원을 넘을 수 있다. 그래서 결제 기한을 측정 시간보다 넉넉히 둔다.
const PAYMENT_DEADLINE_MINUTES = 30;

const created = new Counter('program_apply_created');
const soldOut = new Counter('program_apply_sold_out');
const unexpected = new Counter('program_apply_unexpected');

export const options = {
    setupTimeout: '10m',
    scenarios: {
        open: { executor: 'per-vu-iterations', vus: APPLICANTS, iterations: 1, maxDuration: '5m' },
    },
    thresholds: {
        program_apply_created: [`count==${CAPACITY}`],
        program_apply_unexpected: ['count==0'],
        // 판정용이 아니다. k6 요약에 신청 API의 응답 시간만 따로 보이게 하려고 늘 통과하는 조건을 걸었다.
        'http_req_duration{name:program-apply}': ['max>=0'],
    },
};

function loginApplicants(csrfToken) {
    const sessions = [];
    for (let start = 0; start < APPLICANTS; start += LOGIN_BATCH_SIZE) {
        const emails = [];
        for (let i = start; i < Math.min(start + LOGIN_BATCH_SIZE, APPLICANTS); i++) {
            emails.push(applicantEmail(i));
        }
        const responses = http.batch(emails.map((email) => loginRequest(email, csrfToken)));
        responses.forEach((res, i) => sessions.push(sessionFrom(res, emails[i])));
    }
    return sessions;
}

function createProgram(adminSession, csrfToken) {
    // 서버 시각을 먼저 알아야 신청 시작 시각을 정할 수 있어서, 행사를 만들기 직전에 한 번 더 요청해 Date 헤더를 읽는다.
    const probe = http.get(`${BASE_URL}/api/programs`, { tags: { name: 'clock' } });
    const serverNowMs = Date.parse(probe.headers.Date);
    const localNowMs = Date.now();
    const openAtMs = serverNowMs + OPEN_DELAY_SECONDS * 1000;
    const hour = 60 * 60 * 1000;
    const day = 24 * hour;
    const body = {
        title: `부하 검증 행사 ${new Date(localNowMs).toISOString()}`,
        description: '출시 전 부하 검증용 행사다.',
        locationText: '부하 검증',
        startAt: new Date(serverNowMs + 2 * day).toISOString(),
        endAt: new Date(serverNowMs + 3 * day).toISOString(),
        capacity: CAPACITY,
        fee: 30000,
        applyOpenAt: new Date(openAtMs).toISOString(),
        applyCloseAt: new Date(openAtMs + hour).toISOString(),
        paymentDeadlineMinutes: PAYMENT_DEADLINE_MINUTES,
        overnight: OVERNIGHT,
    };
    const res = http.post(`${BASE_URL}/api/admin/programs`, JSON.stringify(body), {
        headers: { 'Content-Type': 'application/json', ...authHeaders(adminSession, csrfToken) },
        tags: { name: 'program-create' },
    });
    if (res.status !== 201) {
        throw new Error(`행사를 만들지 못했습니다. status=${res.status}, body=${res.body}`);
    }
    // 서버 시각과 우리 시각의 차이만큼 옮겨서, 우리 시계로 언제 출발할지 정한다.
    return { programId: res.json().programId, departAtLocalMs: openAtMs - (serverNowMs - localNowMs) + CLOCK_MARGIN_MS };
}

export function setup() {
    const csrfToken = fetchCsrfToken();
    const adminSession = login(ADMIN_EMAIL, csrfToken);
    const sessions = loginApplicants(csrfToken);
    const program = createProgram(adminSession, csrfToken);
    console.log(`행사 ${program.programId}: 정원 ${CAPACITY}, 신청 회원 ${APPLICANTS}명, 숙박 ${OVERNIGHT}`);
    return { csrfToken, sessions, ...program };
}

export default function (data) {
    unexpected.add(0);
    const waitMs = data.departAtLocalMs - Date.now() + Math.random() * MAX_JITTER_MS;
    if (waitMs > 0) {
        sleep(waitMs / 1000);
    }
    const session = data.sessions[__VU - 1];
    const res = http.post(`${BASE_URL}/api/programs/${data.programId}/applications`, null, {
        headers: { ...authHeaders(session, data.csrfToken), 'Idempotency-Key': crypto.randomUUID() },
        tags: { name: 'program-apply' },
    });
    const code = res.status === 409 ? res.json('code') : null;
    if (res.status === 201) {
        created.add(1);
    } else if (code === 'PROGRAM_SOLD_OUT') {
        soldOut.add(1);
    } else {
        unexpected.add(1);
        console.warn(`예상하지 못한 응답: status=${res.status}, code=${code}`);
    }
    check(res, { '201 또는 매진': (r) => r.status === 201 || code === 'PROGRAM_SOLD_OUT' });
}

export function teardown(data) {
    console.log(
        `DB 대조: SELECT COUNT(*) FROM program_application WHERE program_id = ${data.programId} ` +
            `AND status IN ('PENDING_PAYMENT', 'CONFIRMED'); -- 기대값 ${CAPACITY}`,
    );
}
