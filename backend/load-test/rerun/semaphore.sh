#!/usr/bin/env bash
# 사용: withlock.sh ./semaphore.sh. 신고 쓰기 세마포어(#1022) 전후를 번갈아 잰다(#1023).
# 전은 rerun-1c73c1d2, 후는 rerun-dbe4cec4다. 두 커밋을 각각 체크아웃해 image.sh로 만든다.
# 결과는 results/2026-11-09-write-semaphore/runs/에 쌓이고, 결과가 있는 실행은 건너뛰므로 끊긴 뒤 같은 명령으로 이어서 돈다.
set -euo pipefail
cd "$(dirname "$0")"
export LT_GROUP=2026-11-09-write-semaphore
BEFORE=1c73c1d2 AFTER=dbe4cec4

order() { if (( $1 % 2 )); then echo "before:$BEFORE after:$AFTER"; else echo "after:$AFTER before:$BEFORE"; fi; }

# 11-06 프로토타입(h-pool의 sem-p10-w5)과 같은 조건: 풀 10, 신고 20건, 박지 행 30초 잠금, 디스크 제한, 지도 20 VU. 회차마다 앞뒤를 바꿔 3회.
for n in 1 2 3; do
  for side in $(order $n); do
    LT_OLD_SHA=${side#*:} python3 case2.py "rerun-${side#*:}" "pool-${side%%:*}-$n" 10 20 30 1
  done
done

# 행사 오픈 2,000건(지도 없음). 재시작 직후 1회와 이어서 2회를 한 묶음으로, 회차마다 앞뒤를 바꿔 3묶음.
for n in 1 2 3; do
  for side in $(order $n); do
    sha=${side#*:} s=${side%%:*}
    LT_OLD_SHA=$sha python3 run.py "rerun-$sha" "open-$s-$n-cold" k6_MAP_VUS=0
    LT_OLD_SHA=$sha python3 run.py "rerun-$sha" "open-$s-$n-warm-a" k6_MAP_VUS=0 restart=0 warm=0
    LT_OLD_SHA=$sha python3 run.py "rerun-$sha" "open-$s-$n-warm-b" k6_MAP_VUS=0 restart=0 warm=0
  done
done
echo "semaphore done $(date +%T)"
