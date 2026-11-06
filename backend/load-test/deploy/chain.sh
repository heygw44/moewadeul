#!/usr/bin/env bash
# 배포 묶음 전체. 평소 배포 3회, 막바지에 죽는 버전 3회, 긴 마이그레이션 3회(ROWS, 기본 300만), 관리 포트 전 버전 o1로 되돌리기 1회,
# docs/10 12.2.1 절차. 측정 잠금(/tmp/moewadeul-loadtest.lock)을 잡고 돈다.
set -u
S="$(cd "$(dirname "$0")" && pwd)"
until mkdir /tmp/moewadeul-loadtest.lock 2>/dev/null; do sleep 30; done
trap 'rmdir /tmp/moewadeul-loadtest.lock 2>/dev/null' EXIT
date; uptime
"$S/run-deploy.sh" dep1 n1 n2; "$S/run-deploy.sh" dep2 n2 n1; "$S/run-deploy.sh" dep3 n1 n2
for i in 1 2 3; do AFTER_SLEEP=20 "$S/run-deploy.sh" crash$i n1 k1; done
"$S/migration.sh" 3
if docker image inspect moewadeul-lt-app:o1 > /dev/null 2>&1; then AFTER_SLEEP=20 "$S/run-deploy.sh" old-rollback o1 k1; fi
"$S/flyway-fail.sh"
date; uptime
