#!/usr/bin/env python3
"""사례 2: 박지 행 하나를 mysql 세션으로 잠근 채 그 행에 신고 N건을 보내고, 같은 시간 지도 20 VU의 응답과 Hikari 지표를 잰다.

사용: withlock.sh python3 case2.py <APP_TAG> <이름> <풀> <신고 수> <잠금 초> <디스크 제한 0|1>
앱을 그 풀 크기로 다시 띄우고 지도 20 VU로 30초 데운 뒤 잰다. 결과는 results/$LT_GROUP/runs/<이름>/.
"""
import json, os, subprocess, sys, threading, time, urllib.request, urllib.error

import lt

TAG, NAME, POOL, NREP, HOLD, DISK = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), sys.argv[6] == "1"
VUS = 20
OUT = str(lt.run_dir(NAME))
POST = int(os.environ.get('POST_SEC', '0'))
if os.path.exists(os.path.join(OUT, 'summary.json')):
    print('skip', NAME); sys.exit(0)
lt.require_lock()
META = lt.restart_app(TAG, DISK, LT_POOL=POOL)
subprocess.run(lt.k6(OUT, '/scripts/map-area.js', {'VUS': 20, 'DURATION': '30s'}), capture_output=True)
res_f = open(os.path.join(OUT, 'result.txt'), 'w')


def log(*a):
    line = ' '.join(str(x) for x in a)
    print(line, flush=True); res_f.write(line + '\n'); res_f.flush()


def hikari():
    # 요청 스레드가 모두 막히면 이 요청도 줄을 서서 못 받는다. 그때는 빈 값으로 남긴다.
    try:
        txt = urllib.request.urlopen(lt.MGMT + '/actuator/prometheus', timeout=3).read().decode()
    except Exception:
        return {}
    v = {}
    for l in txt.splitlines():
        for k in ('pending', 'active', 'timeout_total', 'acquire_seconds_max', 'max'):
            if l.startswith('hikaricp_connections_%s{' % k) or l.startswith('hikaricp_connections_%s_total{' % k):
                v[k] = float(l.rsplit(' ', 1)[1])
    return v


def thread_counts():
    # 앱에 jcmd가 없어서 SIGQUIT로 스레드 덤프를 받는다(JVM 안에서 도는 요청 스레드와 그 막힌 자리를 센다).
    subprocess.run(['docker', 'exec', lt.APP, 'kill', '-3', '1'], capture_output=True)
    time.sleep(0.7)
    txt = subprocess.run(['docker', 'logs', '--since', '3s', lt.APP], capture_output=True, text=True)
    txt = (txt.stdout + txt.stderr).rsplit('Full thread dump', 1)[-1]  # 앞 표본의 덤프가 섞이지 않게 마지막 덤프만 쓴다
    exec_n = pool_wait = db_wait = 0
    for blk in txt.split('\n\n'):
        if not blk.lstrip().startswith('"http-nio-8080-exec-'):
            continue
        exec_n += 1
        if 'HikariPool.getConnection' in blk:
            pool_wait += 1
        elif 'com.mysql.cj' in blk:
            db_wait += 1
    return exec_n, pool_wait, db_wait


# (1) 신고 이력이 없고 지도에 보이는 박지. 100만 건 DB라 id 범위로 하나 고른다.
SPOT = int(lt.sql("SELECT s.id FROM spot s JOIN bakji_detail b ON b.spot_id = s.id WHERE s.id > 500000 AND s.status='ACTIVE' "
               "AND NOT EXISTS (SELECT 1 FROM bakji_report r WHERE r.spot_id = s.id) LIMIT 1"))
# (2) 로그인
csrf = lt.csrf_token()
sessions = [lt.login(csrf, 'user%06d@loadtest.invalid' % (i + 1)) for i in range(NREP)]
log(f'# {NAME} spot={SPOT} hold={HOLD}s reports={NREP} map_vus={VUS} start={time.strftime("%FT%T")}')

# (3) 잠금 세션. mysql 클라이언트를 열어 두고 표준 입력으로 명령을 준다.
holder = None
if HOLD > 0:
    holder = subprocess.Popen(
        lt.mysql_args(),
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    holder.stdin.write(f'BEGIN; SELECT id FROM spot WHERE id = {SPOT} FOR UPDATE;\n'.encode()); holder.stdin.flush()
    time.sleep(1.5)
    log('lock held:', lt.sql(f"SELECT COUNT(*) FROM performance_schema.data_locks WHERE OBJECT_NAME='spot' AND LOCK_MODE LIKE 'X%' AND LOCK_DATA='{SPOT}'"))

# (4) 지도 조회 k6. 잠금 구간보다 길게(HOLD + 40초) 돌린다.
K6_SEC = (HOLD if HOLD > 0 else 30) + 40
k6 = subprocess.Popen(
    lt.k6(OUT, '/scripts/map-area.js', {'VUS': VUS, 'DURATION': f'{K6_SEC}s'},
          extra=['--summary-trend-stats', 'med,p(95),p(99),max', '--summary-export=/out/map.json', '--out', 'json=/out/map-points.json']),
    stdout=open(OUT + '/map.log', 'w'), stderr=subprocess.STDOUT)
time.sleep(8)  # 지도 조회가 자리를 잡은 뒤에 신고를 보낸다.
t0 = time.time()
base_h = hikari()
log(f'hikari before reports: {base_h}; t0={time.strftime("%FT%T", time.gmtime(t0))}Z')

# (6) 표본: 4초쯤마다 지표와 스레드 덤프 집계를 남긴다.
samples = []; stop = False; light = []
def conns():
    try: return int(lt.sql("SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_connected'"))
    except ValueError: return None
def sampler():
    while not stop:
        v = hikari(); ex, pw, dw = thread_counts()
        samples.append((time.time() - t0, v.get('pending'), v.get('active'), v.get('timeout_total'), ex, pw, dw, v.get('acquire_seconds_max'), conns(), v.get('max')))
        time.sleep(2)
st_thread = threading.Thread(target=sampler, daemon=True); st_thread.start()

# (5) 신고 동시 전송
reports = [None] * NREP
def send(i):
    s = time.time()
    st, _, raw, _ = lt.request('POST', f'/api/bakjis/{SPOT}/reports', {'reason': 'CLOSED', 'content': 'c62'}, session=sessions[i], csrf=csrf)
    reports[i] = (s - t0, time.time() - s, st, lt.error_code(raw))
threads = [threading.Thread(target=send, args=(i,)) for i in range(NREP)]
for t in threads: t.start()

# (7) 잠금을 HOLD초 뒤에 푼다.
if holder:
    time.sleep(HOLD)
    holder.stdin.write(b'COMMIT;\n'); holder.stdin.flush(); holder.stdin.close()
    log(f'lock released at +{time.time() - t0:.1f}s')
for t in threads: t.join()
log(f'reports done at +{time.time() - t0:.1f}s')
time.sleep(5)
k6.wait()
stop = True
st_thread.join()

from collections import Counter
log('report statuses:', dict(Counter((r[2], r[3]) for r in reports)))
durs = sorted(r[1] for r in reports)
log(f'report latency s: min {durs[0]:.2f} median {durs[len(durs)//2]:.2f} max {durs[-1]:.2f}')
log('samples: t, hikari pending/active/timeouts(cumulative; blank = actuator did not answer in 3s) | request threads in dump / waiting in HikariPool.getConnection / waiting on MySQL')
for t, p, a, to, ex, pw, dw, am, tc, mx in samples:
    log(f'  {t:6.1f} pending={p} active={a} timeouts={to} acquire_max={am} threads_connected={tc} pool={mx} | exec={ex} pool_wait={pw} db_wait={dw}')
log('max exec threads', max(s[4] for s in samples), 'max pool_wait', max(s[5] for s in samples), 'max db_wait', max(s[6] for s in samples),
    'actuator unanswered samples', sum(1 for s in samples if s[1] is None), 'of', len(samples))

# 지도 조회 응답 시간을 구간별로 나눈다: 신고 전(k6 시작 3초 뒤부터 t0까지), 잠금 구간(t0~t0+HOLD), 그 뒤.
import re
from datetime import datetime
def ts(s):
    s = re.sub(r'(\.\d{6})\d+', r'\1', s).replace('Z', '+00:00')
    return datetime.fromisoformat(s).timestamp()
pts = []
for line in open(OUT + '/map-points.json'):
    o = json.loads(line)
    if o.get('type') == 'Point' and o['metric'] == 'http_req_duration' and o['data']['tags'].get('name', '').startswith('spots-area'):
        pts.append((ts(o['data']['time']) - t0, o['data']['value'], o['data']['tags'].get('status')))
def win(label, lo, hi):
    xs = sorted(v for t, v, _ in pts if lo <= t < hi)
    if not xs:
        log(f'map {label}: no samples'); return
    bad = sum(1 for t, _, s in pts if lo <= t < hi and s != '200')
    log(f'map {label}: n={len(xs)} med {xs[len(xs)//2]:.1f}ms p95 {xs[min(len(xs)-1, int(len(xs)*.95))]:.1f}ms max {xs[-1]:.1f}ms non-200={bad}')
# 요청은 응답이 끝난 시각으로 찍히므로 구간 경계가 조금 번진다.
win('before reports', -5, 0)
end_hold = HOLD if HOLD > 0 else 5
win('during lock', 0, end_hold)
win('after lock', end_hold, 1e9)
m = json.load(open(OUT + '/map.json'))['metrics']
d = m['http_req_duration']
log(f'map-area whole run: med {d["med"]:.1f}ms p95 {d["p(95)"]:.1f}ms p99 {d["p(99)"]:.1f}ms max {d["max"]:.1f}ms; failed {m["http_req_failed"]["value"]:.4f}')
since = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(t0))
log('app log lines with Lock wait timeout since t0:', subprocess.run(['docker', 'logs', '--since', since, lt.APP], capture_output=True, text=True).stdout.count('Lock wait timeout'))

# 조건을 한 줄 JSON으로 남긴다. 끊김은 신고 시작 뒤 지도 응답이 완료된 시각 사이의 가장 긴 빈틈이다.
after = sorted((t, v, st) for t, v, st in pts if t >= 0)
gaps = [b[0] - a[0] for a, b in zip(after, after[1:])]
hold_pts = sorted(v for t, v, _ in pts if 0 <= t < 30)
summary = dict(name=NAME, meta=META, hold=HOLD, pool=max((s[9] or 0) for s in samples), reports=NREP, vus=VUS, spot=SPOT,
    report_status=dict(('%s/%s' % k, n) for k, n in Counter((r[2], r[3]) for r in reports).items()),
    report_min=durs[0], report_med=durs[len(durs)//2], report_max=durs[-1],
    map_n=len(pts), map_fail=sum(1 for _, _, st in pts if st != '200'),
    map_p95_30s=hold_pts[int(len(hold_pts)*.95)] if hold_pts else None, map_max=max(v for _, v, _ in pts), map_max_gap_s=max(gaps) if gaps else None,
    pool_wait_max=max(s[5] for s in samples), active_max=max((s[2] or 0) for s in samples), pending_max=max((s[1] or 0) for s in samples),
    acquire_max=max((s[7] or 0) for s in samples), threads_connected_max=max((s[8] or 0) for s in samples),
    timeouts_end=samples[-1][3], actuator_blank=sum(1 for s in samples if s[1] is None), n_samples=len(samples))
json.dump(summary, open(os.path.join(OUT, 'summary.json'), 'w'), ensure_ascii=False)
log('SUMMARY', json.dumps(summary, ensure_ascii=False))
if POST:
    decay = []
    t1 = time.time()
    while time.time() - t1 < POST:
        v = hikari(); decay.append((time.time() - t0, v.get('acquire_seconds_max'), v.get('pending')))
        time.sleep(5)
    json.dump(decay, open(os.path.join(OUT, 'decay.json'), 'w'))
    log('decay', decay)
