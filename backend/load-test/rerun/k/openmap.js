// 행사 오픈과 지도 부하를 같은 시계로 건다. 지도는 오픈 전부터 돌고, 구간별 값은 CSV에서 후처리로 자른다.
import http from 'k6/http';
import { sleep } from 'k6';
import exec from 'k6/execution';
import { ADMIN_EMAIL, BASE_URL, applicantEmail, authHeaders, fetchCsrfToken, login, loginRequest, sessionFrom } from '/scripts/lib/auth.js';
import { pickWeighted, randomPointNearRegion } from '/scripts/lib/regions.js';

const APPLICANTS = Number(__ENV.APPLICANTS || 2000);
const CAPACITY = Number(__ENV.CAPACITY || 200);
const MAP_VUS = Number(__ENV.MAP_VUS || 300);
const MAP_DUR = __ENV.MAP_DUR || '150s';
const OPEN_DELAY = Number(__ENV.OPEN_DELAY || 45);
const ZOOM = [{ level: 13, weight: 10 }, { level: 10, weight: 30 }, { level: 8, weight: 40 }, { level: 5, weight: 20 }];

const scenarios = {};
if (MAP_VUS > 0) scenarios.map = { executor: 'constant-vus', vus: MAP_VUS, duration: MAP_DUR, exec: 'map' };
if (APPLICANTS > 0) scenarios.open = { executor: 'per-vu-iterations', vus: APPLICANTS, iterations: 1, maxDuration: '5m', exec: 'apply' };
export const options = { setupTimeout: '10m', scenarios };

export function setup() {
    if (APPLICANTS === 0) return {};
    const csrf = fetchCsrfToken();
    const admin = login(ADMIN_EMAIL, csrf);
    const sessions = [];
    for (let s = 0; s < APPLICANTS; s += 50) {
        const emails = [];
        for (let i = s; i < Math.min(s + 50, APPLICANTS); i++) emails.push(applicantEmail(i));
        http.batch(emails.map((e) => loginRequest(e, csrf))).forEach((r, i) => sessions.push(sessionFrom(r, emails[i])));
    }
    const probe = http.get(`${BASE_URL}/api/programs`, { tags: { name: 'clock' } });
    const srv = Date.parse(probe.headers.Date);
    const openAt = srv + OPEN_DELAY * 1000;
    const res = http.post(`${BASE_URL}/api/admin/programs`, JSON.stringify({
        title: `부하 ${new Date().toISOString()}`, description: '측정', locationText: '측정',
        startAt: new Date(srv + 172800000).toISOString(), endAt: new Date(srv + 259200000).toISOString(),
        capacity: CAPACITY, fee: 30000, applyOpenAt: new Date(openAt).toISOString(), applyCloseAt: new Date(openAt + 3600000).toISOString(),
        paymentDeadlineMinutes: 30, overnight: true,
    }), { headers: { 'Content-Type': 'application/json', ...authHeaders(admin, csrf), 'Idempotency-Key': crypto.randomUUID() }, tags: { name: 'program-create' } });
    if (res.status !== 201) throw new Error(`행사 생성 실패 ${res.status}`);
    return { csrf, sessions, programId: res.json().programId, departAt: openAt - (srv - Date.now()) + 1000 };
}

export function apply(d) {
    const wait = d.departAt - Date.now() + Math.random() * 500;
    if (wait > 0) sleep(wait / 1000);
    http.post(`${BASE_URL}/api/programs/${d.programId}/applications`, null, {
        headers: { ...authHeaders(d.sessions[exec.scenario.iterationInTest], d.csrf), 'Idempotency-Key': crypto.randomUUID() },
        tags: { name: 'program-apply' }, timeout: '120s',
    });
}

export function map() {
    const { level } = pickWeighted(ZOOM);
    const w = 7.0 / 2 ** (13 - level); const h = w * 0.8;
    const c = level === 13 ? { lat: 36.3, lng: 127.8 } : randomPointNearRegion();
    http.get(`${BASE_URL}/api/spots?swLat=${(c.lat - h / 2).toFixed(6)}&swLng=${(c.lng - w / 2).toFixed(6)}&neLat=${(c.lat + h / 2).toFixed(6)}&neLng=${(c.lng + w / 2).toFixed(6)}&zoom=${level}`,
        { tags: { name: 'spots-area' }, timeout: '60s' });
    sleep(1 + Math.random() * 2);
}
