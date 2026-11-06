#!/usr/bin/env bash
# 사용: lockprobe.sh <잡는 문장 파일> <부딪힐 쓰기 문장 파일>
# 잡는 문장을 트랜잭션 안에서 실행하고 3초 쥔다. 그동안 잡힌 행 잠금 수와, 다른 연결의 쓰기가 기다린 시간을 잰다.
# 운영의 정리 작업은 자동 커밋이라 실제로 쥐는 시간은 그 문장의 실행 시간이다. 여기서는 잠금 범위만 본다.
. "$(dirname "$0")/env.sh"
S="$S/sql.sh"
( printf "START TRANSACTION;\n%s;\nDO SLEEP(3);\nROLLBACK;\n" "$(cat "$1")" | "$S" --batch >/dev/null 2>&1 ) &
sleep 1
"$S" --batch --skip-column-names -e "SELECT COUNT(*), SUM(LOCK_MODE LIKE '%GAP%' OR LOCK_MODE='X' OR LOCK_MODE='S') FROM performance_schema.data_locks WHERE LOCK_TYPE='RECORD' AND OBJECT_SCHEMA='moewadeul'" | awk '{print "row_locks", $1}'
start=$(python3 -c 'import time;print(time.time())')
printf "SET SESSION innodb_lock_wait_timeout=10;\nSTART TRANSACTION;\n%s;\nROLLBACK;\n" "$(cat "$2")" | "$S" --batch >/dev/null 2>&1
python3 -c "import time;print('writer_wait_s', round(time.time()-$start,2))"
wait
