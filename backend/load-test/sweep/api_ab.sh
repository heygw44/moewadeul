#!/usr/bin/env bash
# 사용: api_ab.sh <API 슬러그> <만들 DDL> <지울 DDL> [회차=3]
# 같은 앱 이미지에서 인덱스만 넣고 빼며 혼자 재기(60건)와 50 VU 15초를 번갈아 잰다. 부하 중 앱·MySQL CPU를 1초마다 적는다.
. "$(dirname "$0")/env.sh"; cd "$D"
SLUG=$1; ADD=$2; DROP=$3; N=${4:-3}; OUTD=ab/api-$SLUG; mkdir -p $OUTD
run() { docker run --rm -u 0 --network moewadeul-lt-sweep_default --cpuset-cpus 4-7 -v $D:/d -v $S:/s:ro grafana/k6 run -q --no-usage-report -e PLAN=/d/out/plans.orig/$SLUG.json -e BASE_URL=http://caddy:80 -e OUT=/d/$OUTD/$1.json "${@:2}" /s/replay.js > /dev/null 2>&1; }
for i in $(seq 1 $N); do
  for side in before after; do
    [ $side = after ] && $S/sql.sh --batch -e "$ADD"
    run seq-$side-$i -e MODE=seq -e COUNT=60
    ( for s in $(seq 1 15); do docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' moewadeul-lt-sweep-app-1 moewadeul-lt-sweep-mysql-1 | tr '\n' ' '; echo; done ) > $OUTD/cpu-$side-$i.txt &
    run load-$side-$i -e MODE=load -e DURATION=15s -e VUS=50
    wait
    [ $side = after ] && $S/sql.sh --batch -e "$DROP"
  done
done
python3 - "$OUTD" <<'PY'
import json,glob,sys,statistics,re
d=sys.argv[1]
for side in ("before","after"):
    seq=[json.load(open(f)) for f in sorted(glob.glob(f"{d}/seq-{side}-*.json"))]
    load=[json.load(open(f)) for f in sorted(glob.glob(f"{d}/load-{side}-*.json"))]
    cpu=[]
    for f in glob.glob(f"{d}/cpu-{side}-*.txt"):
        for ln in open(f):
            m=re.findall(r"(app|mysql)-1 ([\d.]+)%",ln)
            if len(m)==2: cpu.append((float(m[0][1]),float(m[1][1])))
    med=lambda xs: statistics.median(xs) if xs else 0
    print(f"{side}: 혼자 p50 {med([s['ok']['med'] for s in seq]):.1f} p95 {med([s['ok']['p(95)'] for s in seq]):.1f} ms | 50VU p95 {med([l['ok']['p(95)'] for l in load]):.0f} ms (범위 {min(l['ok']['p(95)'] for l in load):.0f}~{max(l['ok']['p(95)'] for l in load):.0f}), 초당 {med([l['rate'] for l in load]):.1f}건, 2xx 아님 {sum(sum(v for k,v in l['statuses'].items() if not k.startswith('2')) for l in load)} | 앱 CPU 중앙 {med([c[0] for c in cpu]):.0f}% MySQL {med([c[1] for c in cpu]):.0f}% ({len(seq)}회)")
PY
