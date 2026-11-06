#!/usr/bin/env python3
"""run-deploy.sh 결과 한 건을 요약한다. 사용: analyze.py <결과 폴더>/runs/<이름>

- 요청 결과: k6-requests.log(요청 하나에 한 줄). 시각은 요청을 보낸 때다.
  실패 = 응답 코드가 200(GET)·201(POST)이 아닌 것. 502와 연결 오류(코드 0)를 따로 센다.
- 중단 시간: (가) 첫 실패~마지막 실패, (나) 마지막 성공(첫 실패 직전)~첫 성공(마지막 실패 직후). (나)가 초당 2건이라 0.5초 안에서 정확하다.
- 컨테이너 단계: docker events의 app 컨테이너 die → 새 app start, 그리고 deploy.sh가 앱 상태 확인 통과를 찍은 시각.
"""
import re, sys, collections, statistics, pathlib

run = pathlib.Path(sys.argv[1])
rows = []   # (요청을 보낸 시각(초), 종류, 응답 코드, 응답 시간 ms)
for l in open(run / 'k6-requests.log'):
    m = re.search(r'msg="R (\d+) (GET|POST) (\d+) ([\d.]+) ', l)
    if m:
        rows.append((int(m[1]) / 1000, 'GET programs' if m[2] == 'GET' else 'POST comment', m[3], float(m[4])))
rows.sort()
t0 = float(next(l for l in open(run / 'timeline.txt') if l.startswith('deploy_start')).split()[1])
t1 = float(next(l for l in open(run / 'timeline.txt') if l.startswith('deploy_end')).split()[1])

total = len(rows)
by = collections.Counter()
for ts, name, status, dur in rows:
    by[(name, status)] += 1
fails = [r for r in rows if r[2] not in ('200', '201')]
print(f'요청 {total}건, 실패 {len(fails)}건 ({100*len(fails)/total:.1f}%)')
for (name, status), n in sorted(by.items()):
    print(f'  {name:14s} 응답 {status:>3s}: {n}')
for name in ('GET programs', 'POST comment'):
    mine = [r for r in rows if r[1] == name]
    f = [r for r in mine if r[2] not in ('200', '201')]
    print(f'  {name}: 보냄 {len(mine)}, 실패 {len(f)}')
if fails:
    first, last = fails[0][0], fails[-1][0]
    before = [r[0] for r in rows if r[0] < first and r[2] in ('200', '201')]
    after = [r[0] for r in rows if r[0] > last and r[2] in ('200', '201')]
    print(f'실패 구간(첫 실패~마지막 실패): {last-first:.1f}초')
    if before and after:
        print(f'성공 사이 간격(마지막 성공~첫 성공): {after[0]-before[-1]:.1f}초')
    elif before:
        print(f'측정 끝까지 복구 안 됨: 마지막 성공 뒤 {rows[-1][0]-before[-1]:.1f}초 이상 중단')
    print(f'첫 실패는 deploy.sh 시작 {first-t0:+.1f}초, 마지막 실패는 deploy.sh 끝 {last-t1:+.1f}초')
    slow = [d for _, _, s, d in fails]
    print(f'실패 요청의 응답 시간: 중앙값 {statistics.median(slow):.0f}ms, 최대 {max(slow):.0f}ms')
    # 실패 구간 안에서 성공이 섞여 있는지(깜빡임)
    inside = [r for r in rows if first <= r[0] <= last and r[2] in ('200', '201')]
    print(f'실패 구간 안의 성공: {len(inside)}건')
else:
    print('실패 없음')
print(f'deploy.sh 실행 시간: {t1-t0:.1f}초')

# 컨테이너 이벤트
ev = []
for l in open(run / 'events.raw'):
    p = l.split()
    if len(p) >= 3 and p[0].isdigit():
        ev.append((int(p[0]) / 1e9, p[1], p[2]))
print('컨테이너 이벤트(deploy.sh 시작 기준 초):')
for ts, name, act in ev:
    if ts >= t0 and act in ('kill', 'die', 'start', 'stop', 'create') and 'smtp' not in name:
        print(f'  {ts-t0:7.1f}  {name:24s} {act}')
for l in open(run / 'deploy.log'):
    if any(k in l for k in ('상태 확인 통과', 'caddy 응답 확인 통과', '배포 성공', '배포 실패', '되돌리기', '되돌린다', '되돌렸다', '정상 상태가 되지 않았다', 'mysql 정의', '다시 시작했다', 'dependency', 'unhealthy')):
        print(f'  {float(l.split()[0])-t0:7.1f}  deploy.sh: {l.split(" ",1)[1].strip()}')

# 지연: Caddy가 붙잡고 기다린 요청은 실패 대신 느린 성공이 된다. 종류별 p95·최대를 배포 구간(시작~끝+5초)과 전체로 본다.
def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))] if xs else 0
for name in ('GET programs', 'POST comment'):
    allx = [d for ts, n, s, d in rows if n == name]
    win = [d for ts, n, s, d in rows if n == name and t0 <= ts <= t1 + 5]
    print(f'지연 {name}: 전체 p95 {pct(allx, .95):.0f}ms 최대 {max(allx, default=0):.0f}ms / 배포 구간 {len(win)}건 p95 {pct(win, .95):.0f}ms 최대 {max(win, default=0):.0f}ms')
ok_post = sum(1 for r in rows if r[1] == 'POST comment' and r[2] == '201')
print(f'POST 201 응답 {ok_post}건')
