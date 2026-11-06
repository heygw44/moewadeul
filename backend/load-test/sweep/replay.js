// sweep.py가 만든 요청 묶음을 그대로 다시 보낸다. MODE=seq는 혼자 한 건씩, MODE=load는 VUS명이 동시에 보낸다.
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const plan = JSON.parse(open(__ENV.PLAN));
const MODE = __ENV.MODE || 'seq';
const VUS = Number(__ENV.VUS || 50);
const SKIP = Number(__ENV.SKIP || 0);
const COUNT = Number(__ENV.COUNT || plan.requests.length);
const DURATION = __ENV.DURATION || '';
const BASE = __ENV.BASE_URL;
const OUT = __ENV.OUT;

const okDur = new Trend('ok_dur', true);
const allDur = new Trend('all_dur', true);
const CODES = [0, 200, 201, 202, 204, 301, 302, 304, 400, 401, 403, 404, 405, 409, 410, 413, 415, 422, 423, 429, 500, 502, 503, 504];
const statuses = Object.fromEntries(CODES.map((c) => [c, new Counter(`st_${c}`)]));
const other = new Counter('st_other');

const scenario =
  DURATION !== ''
    ? { executor: 'constant-vus', vus: VUS, duration: DURATION }
    : { executor: 'shared-iterations', vus: MODE === 'seq' ? 1 : VUS, iterations: COUNT, maxDuration: __ENV.MAX || '5m' };

export const options = {
  scenarios: { s: scenario },
  summaryTrendStats: ['count', 'avg', 'med', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
  insecureSkipTLSVerify: true,
  noCookiesReset: true,
};

let errorsPrinted = 0;

export default function () {
  const i = exec.scenario.iterationInTest;
  const r = plan.requests[SKIP + (i % (plan.requests.length - SKIP))];
  const res = http.request(r.m, (plan.base || BASE) + r.u, r.b === undefined ? null : r.b, {
    headers: r.h,
    redirects: 0,
    timeout: '60s',
    responseType: 'text',
    jar: new http.CookieJar(),
  });
  allDur.add(res.timings.duration);
  (statuses[res.status] || other).add(1);
  const ok = plan.ok ? plan.ok.includes(res.status) : res.status >= 200 && res.status < 300;
  if (ok) {
    okDur.add(res.timings.duration);
  } else if (errorsPrinted < 2) {
    errorsPrinted += 1;
    console.log(`BAD ${res.status} ${r.m} ${r.u} ${String(res.body).slice(0, 220)}`);
  }
}

export function handleSummary(data) {
  const m = data.metrics;
  const v = (name) => (m[name] ? m[name].values : {});
  const out = {
    api: plan.api,
    mode: MODE,
    all: v('all_dur'),
    ok: v('ok_dur'),
    count: v('all_dur').count || 0,
    statuses: {},
    rate: data.state.testRunDurationMs ? (v('all_dur').count || 0) / (data.state.testRunDurationMs / 1000) : 0,
  };
  for (const key of Object.keys(m)) {
    if (key.startsWith('st_')) out.statuses[key.slice(3)] = m[key].values.count;
  }
  return { [OUT]: JSON.stringify(out) };
}
