// #903 측정용: 지도, 날씨, AI 검색, 사진 첨부 기록을 함께 건다. 켜고 끄는 값은 모두 환경 변수다.
// VUS 지도 VU | WEATHER_RATE 날씨 초당 건수 | HOT_RATE 찬 격자 집중 초당 건수 | AI_RATE AI 초당 건수 | ATTACH_VUS 첨부 VU
// 첨부가 503이면 같은 사진으로 다시 시도한다. 새로 발급하면 회원당 미첨부 30장 상한에 걸려 측정이 무효가 된다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { BASE_URL, applicantEmail, authHeaders, fetchCsrfToken, loginRequest, sessionFrom } from '/scripts/lib/auth.js';
import { pickWeighted, randomPointNearRegion } from '/scripts/lib/regions.js';

const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '3m';
const WEATHER_RATE = Number(__ENV.WEATHER_RATE || 0);
const HOT_RATE = Number(__ENV.HOT_RATE || 0);
const HOT_ROTATE_MS = Number(__ENV.HOT_ROTATE_MS || 1700);
const AI_RATE = Number(__ENV.AI_RATE || 0);
const ATTACH_VUS = Number(__ENV.ATTACH_VUS || 0);
const PHOTOS = Number(__ENV.PHOTOS || 10);
const SIZE = 100000;
const AI_MEMBERS = 60;
const AI_MEMBER_OFFSET = 100;
const ATTACH_MEMBER_OFFSET = 1500;
const SPOT_IDS = new SharedArray('ids', () => JSON.parse(open('/out/spot-ids.json')));

const weatherEmpty = new Counter('weather_empty');
const aiOk = new Counter('ai_ok');
const ai503 = new Counter('ai_503');
const ai429 = new Counter('ai_429');
const aiOther = new Counter('ai_other');
const attachOk = new Counter('attach_ok');
const attach503 = new Counter('attach_503');
const attach429 = new Counter('attach_429');
const attachOther = new Counter('attach_other');
const issueFail = new Counter('issue_fail');
const aiOkMs = new Trend('ai_ok_ms', true);
const attachOkMs = new Trend('attach_ok_ms', true);
const attachFailMs = new Trend('attach_fail_ms', true);

function arrival(rate, fn) {
    return { executor: 'constant-arrival-rate', rate, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 100, maxVUs: 400, exec: fn };
}
const scenarios = {};
if (VUS > 0) scenarios.map = { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'map' };
if (WEATHER_RATE > 0) scenarios.weather = arrival(WEATHER_RATE, 'weather');
if (HOT_RATE > 0) scenarios.hot = arrival(HOT_RATE, 'hot');
if (AI_RATE > 0) scenarios.ai = arrival(AI_RATE, 'ai');
if (__ENV.ONCE) scenarios.attach = { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '3m', exec: 'attach' };
else if (ATTACH_VUS > 0) scenarios.attach = { executor: 'constant-vus', vus: ATTACH_VUS, duration: DURATION, exec: 'attach', gracefulStop: '120s' };

const tagged = {};
for (const name of ['spots-area', 'spot-weather', 'ai-interpret', 'memory-create', 'memory-image']) {
    tagged[`http_reqs{name:${name}}`] = ['count>=0'];
    tagged[`http_req_duration{name:${name}}`] = ['max>=0'];
    tagged[`http_req_failed{name:${name}}`] = ['rate>=0'];
}
export const options = { scenarios, setupTimeout: '3m', summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'], thresholds: tagged };

function loginAll(offset, count, csrfToken) {
    const emails = Array.from({ length: count }, (_, i) => applicantEmail(offset + i));
    return http.batch(emails.map((email) => loginRequest(email, csrfToken))).map((res, i) => sessionFrom(res, emails[i]));
}

export function setup() {
    const csrfToken = fetchCsrfToken();
    const aiSessions = AI_RATE > 0 ? loginAll(AI_MEMBER_OFFSET, AI_MEMBERS, csrfToken) : [];
    const attachSessions = ATTACH_VUS > 0 ? loginAll(ATTACH_MEMBER_OFFSET, ATTACH_VUS, csrfToken) : [];
    console.log('SETUP_DONE');
    return { csrfToken, aiSessions, attachSessions };
}

// 화면 비율은 지난 지도 측정(map-weather.js)과 같다.
const ZOOM_LEVELS = [
    { level: 13, weight: 10 },
    { level: 10, weight: 30 },
    { level: 8, weight: 40 },
    { level: 5, weight: 20 },
];
export function map() {
    const { level } = pickWeighted(ZOOM_LEVELS);
    const lngSpan = 7.0 / 2 ** (13 - level);
    const latSpan = lngSpan * 0.8;
    const center = level === 13 ? { lat: 36.3, lng: 127.8 } : randomPointNearRegion();
    const url = `${BASE_URL}/api/spots?swLat=${(center.lat - latSpan / 2).toFixed(6)}&swLng=${(center.lng - lngSpan / 2).toFixed(6)}`
        + `&neLat=${(center.lat + latSpan / 2).toFixed(6)}&neLng=${(center.lng + lngSpan / 2).toFixed(6)}&zoom=${level}`;
    const res = http.get(url, { tags: { name: 'spots-area', level: String(level) }, timeout: '60s' });
    check(res, { '지도 200': (r) => r.status === 200 });
    sleep(1 + Math.random() * 2);
}

function getWeather(id) {
    const res = http.get(`${BASE_URL}/api/spots/${id}/weather`, { tags: { name: 'spot-weather' }, timeout: '60s' });
    check(res, { '날씨 200': (r) => r.status === 200 });
    if (res.status === 200 && res.json().weather === null) weatherEmpty.add(1);
}
export function weather() {
    getWeather(SPOT_IDS[Math.floor(Math.random() * SPOT_IDS.length)]);
}
// 같은 시간 칸의 요청은 모두 같은 찬 장소로 가서, 먼저 간 호출을 기다리는 자리를 채운다.
export function hot() {
    getWeather(SPOT_IDS[Math.floor(Date.now() / HOT_ROTATE_MS) % SPOT_IDS.length]);
}

export function ai(data) {
    const session = data.aiSessions[exec.scenario.iterationInTest % data.aiSessions.length];
    const res = http.post(`${BASE_URL}/api/spots/ai-interpret`, JSON.stringify({ query: '강릉에서 물 나오는 차박지 찾아줘' }), {
        headers: { ...authHeaders(session, data.csrfToken), 'Content-Type': 'application/json' },
        tags: { name: 'ai-interpret' },
        timeout: '60s',
    });
    if (res.status === 200) {
        aiOk.add(1);
        aiOkMs.add(res.timings.duration);
    } else if (res.status === 503) ai503.add(1);
    else if (res.status === 429) ai429.add(1);
    else aiOther.add(1);
}

let pending = [];
let attachKey = null;
export function attach(data) {
    const session = data.attachSessions[(__VU - 1) % data.attachSessions.length];
    const headers = { ...authHeaders(session, data.csrfToken), 'Content-Type': 'application/json' };
    while (pending.length < PHOTOS) {
        const res = http.post(`${BASE_URL}/api/memories/images`,
            JSON.stringify({ contentType: 'image/jpeg', sizeBytes: SIZE, thumbnailSizeBytes: SIZE }),
            { headers, tags: { name: 'memory-image' }, timeout: '60s' });
        if (res.status !== 201) {
            issueFail.add(1);
            sleep(1);
            return;
        }
        pending.push(res.json().imageId);
    }
    const visitedDate = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
    // 화면처럼 재시도에는 같은 키를 쓴다.
    attachKey = attachKey || `lt-${__VU}-${Date.now()}`;
    const res = http.post(`${BASE_URL}/api/memories`, JSON.stringify({ lat: 37.5, lng: 127.5, visitedDate, nights: 0, imageIds: pending }), {
        headers: { ...headers, 'Idempotency-Key': attachKey }, tags: { name: 'memory-create' }, timeout: '180s',
    });
    if (res.status === 201) {
        attachOk.add(1);
        attachOkMs.add(res.timings.duration);
        pending = [];
        attachKey = null;
    } else {
        attachFailMs.add(res.timings.duration);
        if (res.status === 503) attach503.add(1);
        else if (res.status === 429) attach429.add(1);
        else {
            attachOther.add(1);
            console.log(`attach ${res.status} ${res.body}`);
            pending = [];
            attachKey = null;
        }
    }
    sleep(1 + Math.random() * 2);
}
