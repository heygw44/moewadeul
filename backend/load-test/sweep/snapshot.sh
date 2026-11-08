#!/usr/bin/env bash
# 사용: snapshot.sh save|restore. 시드를 마친 데이터 볼륨을 moewadeul-lt-sweep-seeded에 얼리거나 되돌린다. MySQL을 멈춘 상태에서만 복사한다.
set -euo pipefail
. "$(dirname "$0")/env.sh"
"$S/lt.sh" stop -t 60 app caddy mysql >/dev/null 2>&1 || true
"$S/lt.sh" rm -f mysql >/dev/null 2>&1 || true
if [ "$1" = save ]; then SRC=moewadeul-lt-sweep_mysql-data; DST=${2:-moewadeul-lt-sweep-seeded}; docker volume create "$DST" >/dev/null
else SRC=${2:-moewadeul-lt-sweep-seeded}; DST=moewadeul-lt-sweep_mysql-data; fi
docker run --rm --cpuset-cpus 4-7 -v "$SRC":/from:ro -v "$DST":/to alpine sh -c 'rm -rf /to/* /to/.[!.]* 2>/dev/null; cd /from && cp -a . /to/ && du -sh /to'
