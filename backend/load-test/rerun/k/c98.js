// 후보 98·107 부하: 행사 신청(apply)과 결제·취소(pay/cancel)를 한 행사에 섞는다. 세션은 로그인 없이 /out/plan.json에서 읽는다.
// MODE=mix|apply|pay. 환경 변수: APPLY_VUS ROUNDS ROUND_SLEEP(회차 사이 평균 초) PAY_VUS PAY_ACTIONS JITTER_MS MAX_DURATION
// plan.json: { csrf, programId }, plan-apply.json: [세션 문자열...](APPLY_VUS*ROUNDS개), plan-pay.json: [{s, id, op}...](PAY_VUS*PAY_ACTIONS개)
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const MODE = __ENV.MODE || 'mix';
const APPLY_VUS = Number(__ENV.APPLY_VUS || 300);
const ROUNDS = Number(__ENV.ROUNDS || 4);
const ROUND_SLEEP = Number(__ENV.ROUND_SLEEP || 7);
const PAY_VUS = Number(__ENV.PAY_VUS || 100);
const PAY_ACTIONS = Number(__ENV.PAY_ACTIONS || 5);
const JITTER_MS = Number(__ENV.JITTER_MS || 500);
const MAX_DURATION = __ENV.MAX_DURATION || '10m';
const plan = JSON.parse(open('/out/plan.json'));
const applySessions = new SharedArray('apply', () => JSON.parse(open('/out/plan-apply.json')));
const payItems = new SharedArray('pay', () => JSON.parse(open('/out/plan-pay.json')));

const KNOWN = {
    apply: ['201', '409_PROGRAM_SOLD_OUT', '409_PROGRAM_ALREADY_APPLIED', '409_IDEMPOTENCY_IN_PROGRESS', '503_RESOURCE_BUSY', '500', '401', '403', '429'],
    pay: ['200', '409_PROGRAM_PAYMENT_EXPIRED', '409_PROGRAM_INVALID_STATE', '409_IDEMPOTENCY_IN_PROGRESS', '503_RESOURCE_BUSY', '500', '401', '403', '404'],
    cancel: ['200', '409_PROGRAM_INVALID_STATE', '409_PROGRAM_CANCEL_NOT_ALLOWED', '503_RESOURCE_BUSY', '500', '401', '403', '404'],
};
const counters = {};
const trends = {};
for (const ep of Object.keys(KNOWN)) {
    for (const key of [...KNOWN[ep], 'other']) {
        counters[`${ep}_${key}`] = new Counter(`${ep}_n_${key}`);
    }
    for (const cls of ['ok', 'busy', 'err']) {
        trends[`${ep}_${cls}`] = new Trend(`${ep}_ms_${cls}`, true);
    }
}

const scenarios = {};
if (MODE !== 'pay') {
    scenarios.apply = { executor: 'per-vu-iterations', vus: APPLY_VUS, iterations: ROUNDS, maxDuration: MAX_DURATION, exec: 'applyRound' };
}
if (MODE !== 'apply') {
    scenarios.pay = { executor: 'per-vu-iterations', vus: PAY_VUS, iterations: PAY_ACTIONS, maxDuration: MAX_DURATION, exec: 'payRound' };
}
export const options = {
    scenarios,
    thresholds: Object.fromEntries(
        Object.keys(KNOWN).flatMap((ep) => ['ok', 'busy', 'err'].map((cls) => [`${ep}_ms_${cls}`, ['max>=0']]))
            .concat(Object.keys(KNOWN).map((ep) => [`http_req_duration{name:${ep}}`, ['max>=0']])),
    ),
};

function headers(session, extra) {
    return { Cookie: `SESSION=${session}; XSRF-TOKEN=${plan.csrf}`, 'X-XSRF-TOKEN': plan.csrf, 'Content-Type': 'application/json', ...extra };
}

function record(ep, res) {
    let code = '';
    if (res.status >= 400) {
        try { code = res.json('code') || ''; } catch (e) { code = ''; }
    }
    const key = `${res.status}${code && (res.status === 409 || res.status === 503) ? '_' + code : ''}`;
    const name = KNOWN[ep].includes(key) ? key : 'other';
    counters[`${ep}_${name}`].add(1);
    const cls = res.status < 300 ? 'ok' : res.status === 503 ? 'busy' : 'err';
    trends[`${ep}_${cls}`].add(res.timings.duration);
}

function pause() {
    if (ROUND_SLEEP > 0) { sleep(ROUND_SLEEP * (0.5 + Math.random())); }
}

export function applyRound() {
    if (exec.scenario.iterationInInstance < APPLY_VUS && JITTER_MS > 0) { sleep((Math.random() * JITTER_MS) / 1000); }
    const session = applySessions[exec.scenario.iterationInTest];
    const res = http.post(`${BASE_URL}/api/programs/${plan.programId}/applications`, null, {
        headers: headers(session, { 'Idempotency-Key': crypto.randomUUID() }), tags: { name: 'apply' },
    });
    record('apply', res);
    pause();
}

export function payRound() {
    if (exec.scenario.iterationInInstance < PAY_VUS && JITTER_MS > 0) { sleep((Math.random() * JITTER_MS) / 1000); }
    const item = payItems[exec.scenario.iterationInTest];
    let res;
    if (item.op === 'pay') {
        res = http.post(`${BASE_URL}/api/program-applications/${item.id}/pay`, JSON.stringify({ method: 'FAKE_CARD' }), {
            headers: headers(item.s, { 'Idempotency-Key': crypto.randomUUID() }), tags: { name: 'pay' },
        });
        record('pay', res);
    } else {
        res = http.post(`${BASE_URL}/api/program-applications/${item.id}/cancel`, null, { headers: headers(item.s), tags: { name: 'cancel' } });
        record('cancel', res);
    }
    pause();
}
