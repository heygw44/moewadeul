#!/usr/bin/env bash
# 사용: withlock.sh <명령...>. 공유 측정 잠금(/tmp/moewadeul-loadtest.lock)을 얻어 caffeinate 안에서 돌리고 놓는다.
. "$(dirname "$0")/env.sh"
until mkdir "$LT_LOCK" 2>/dev/null; do sleep 30; done
trap 'rm -f "$LT_LOCK/owner"; rmdir "$LT_LOCK"' EXIT
echo "rerun $$ $(date '+%F %T') $*" > "$LT_LOCK/owner"
echo "lock $(date +%T)"; caffeinate -i "$@"; rc=$?; echo "unlock $(date +%T) rc=$rc"; exit $rc
