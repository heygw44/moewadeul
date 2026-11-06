#!/usr/bin/env bash
# 사용: abloop.sh "문장 쓰기" ... 각 쌍마다 문장 시간과 잠금 범위를 잰다.
. "$(dirname "$0")/env.sh"; cd "$D"
for p in "$@"; do set -- $p
  echo "== $1 $(python3 $S/qtime.py ${N:-3} ab/$1.sql)"
  [ -n "${2:-}" ] && $S/lockprobe.sh ab/$1.sql ab/$2.sql | tr '\n' ' ' && echo
done
