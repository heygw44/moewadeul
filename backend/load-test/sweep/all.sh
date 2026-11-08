#!/usr/bin/env bash
. "$(dirname "$0")/env.sh"; cd "$D"
# 11-06 사용자 지시로 잠금 없이 다른 줄과 동시에 돈다. 값은 찾기용이다.
echo "start $(date)"
$S/pipeline.sh prepare 2>&1 | tail -4
echo "prepared $(date)"
$S/pipeline.sh map 2>&1 | tail -20
echo "mapped $(date)"
$S/pipeline.sh measure 2>&1 | tail -20
echo "measured $(date)"
