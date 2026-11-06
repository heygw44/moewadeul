#!/usr/bin/env bash
# 사용: pipeline.sh prepare|map|measure. 잠금은 호출한 쪽이 잡는다.
set -uo pipefail
. "$(dirname "$0")/env.sh"; cd "$D"
case "$1" in
prepare)
  rm -rf out/plans out/plans.orig out/sessions.json out/csrf.json out/cursor.json
  $S/snapshot.sh restore
  $S/up.sh
  python3 $S/sweep.py plan > logs/plan.log 2>&1
  grep -E "요청 없음|계획 |오류|로그인 실패" logs/plan.log | cut -c1-200 | head -8
  cp -R out/plans out/plans.orig
  $S/lt.sh stop -t 40 app caddy >/dev/null 2>&1
  $S/sql.sh --batch < $S/pre_run.sql
  $S/snapshot.sh save moewadeul-lt-sweep-planned
  ;;
map)
  rm -rf out/plans; cp -R out/plans.orig out/plans; rm -f out/smoke.json out/smoke-map.json
  $S/snapshot.sh restore moewadeul-lt-sweep-planned
  LT_JOBS_ENV=$S/jobs.env $S/up.sh debug
  SMOKE_OUT=smoke-map python3 $S/sweep.py smoke > logs/smoke-map.log 2>&1
  curl -s http://127.0.0.1:47181/actuator/prometheus > /dev/null
  tail -1 logs/smoke-map.log | cut -c1-600
  grep -vE '^2[0-9][0-9] ' logs/smoke-map.log | cut -c1-360 | head -40
  # 스케줄 작업이 두 바퀴 돌도록 기다린다.
  sleep 270
  docker logs moewadeul-lt-sweep-app-1 2>/dev/null > logs/app-map.log
  $S/sql.sh --batch -e "SELECT 1" >/dev/null 2>&1
  python3 $S/maplog.py map < logs/app-map.log
  ;;
measure)
  rm -rf out/plans out/seq out/load out/warm out/cursor.json; cp -R out/plans.orig out/plans
  $S/snapshot.sh restore moewadeul-lt-sweep-planned
  $S/up.sh
  python3 $S/sweep.py warm > logs/warm.log 2>&1
  tail -1 logs/warm.log | cut -c1-200
  python3 $S/sweep.py reset-digest
  python3 $S/sweep.py seq > logs/seq.log 2>&1
  python3 $S/sweep.py digest seq
  python3 $S/sweep.py reset-digest
  python3 $S/sweep.py load > logs/load.log 2>&1
  python3 $S/sweep.py digest load
  python3 $S/sweep.py reset-digest
  LT_JOBS_ENV=$S/jobs.env $S/up.sh
  curl -s http://127.0.0.1:47181/actuator/prometheus > /dev/null
  sleep 270
  python3 $S/sweep.py digest jobs
  docker logs moewadeul-lt-sweep-app-1 2>/dev/null | grep -E 'failed|"level":"ERROR"' | cut -c1-300 | tail -5
  $S/lt.sh stop -t 40 app caddy >/dev/null 2>&1
  python3 $S/render.py > logs/render.log 2>&1
  python3 $S/explain.py > logs/explain.log 2>&1
  python3 $S/summary.py
  ;;
esac
