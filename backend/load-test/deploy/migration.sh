#!/usr/bin/env bash
# 사용: migration.sh [회차 수]. 알림 표를 ROWS(기본 300만) 행까지 채우고, 인덱스 두 개를 만드는 V999가 든 m1을 배포하며 실패를 센다.
# 회차마다 V999 이력과 인덱스를 지운다. 실행 이름은 ${MIGNAME:-mig}<회차>다.
set -u
source "$(dirname "$0")/env.sh"
N=${1:-3} ROWS=${ROWS:-3000000}
cnt=$(q -N -e "SELECT COUNT(*) FROM notification")
while [ "$cnt" -lt "$ROWS" ]; do
    q -e "SET SESSION cte_max_recursion_depth=200000; INSERT INTO notification (member_id, type, title, body, link, dedup_key, email_status, created_at)
      SELECT m.id, 'BASECAMP_APPLIED', '측정 알림', '측정 알림 본문', NULL, CONCAT('lb-', $cnt + s.n), 'SKIPPED', UTC_TIMESTAMP(6) - INTERVAL ($cnt + s.n) SECOND
      FROM (SELECT MIN(id) id FROM member) m JOIN (WITH RECURSIVE t(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM t WHERE n < 100000) SELECT n FROM t) s"
    cnt=$(q -N -e "SELECT COUNT(*) FROM notification")
done
echo "notification rows $cnt"
for i in $(seq 1 "$N"); do
    name=${MIGNAME:-mig}$i
    "$S/run-deploy.sh" "$name" n1 m1
    q -N -e "SELECT version, execution_time, success FROM flyway_schema_history WHERE version='999'" | tee -a "$D/runs/$name/summary.txt"
    q -e "DELETE FROM flyway_schema_history WHERE version='999'; DROP INDEX idx_lb_probe_a ON notification; DROP INDEX idx_lb_probe_b ON notification"
done
