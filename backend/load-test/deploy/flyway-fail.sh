#!/usr/bin/env bash
# docs/10 12.2.1(실패한 마이그레이션이 이력에 남았을 때)을 그대로 따라 한다.
# f1의 V999는 인덱스를 만든 뒤 표 lt_fail_probe를 만든다. 같은 이름 표를 미리 만들어 둘째 문장이 실패하게 한다.
# 원인(남은 표)을 치우면 4단계 '같은 태그를 다시 배포'가 성공해야 한다. 결과는 runs/flyway-fail/summary.txt에 남는다.
set -u
source "$(dirname "$0")/env.sh"
OUT="$D/runs/flyway-fail" DEP="$OPT/deploy.lt.sh"
mkdir -p "$OUT"; F="$OUT/summary.txt"; : > "$F"
# 문서의 서버 명령. compose 인자만 측정 틀(-p, override)에 맞춘다.
docq() { (cd "$OPT" && docker compose -p "$P" -f docker-compose.prod.yml -f override.yml exec -T mysql \
    sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot \"\$MYSQL_DATABASE\" -e \"$1\""); }
now() { python3 -c 'import time;print(time.time())'; }
since() { python3 -c "import time;print(f'{time.time()-$1:.1f}')"; }
count() { q -N -e "SELECT COUNT(*) FROM information_schema.$1 WHERE table_schema=DATABASE() AND $2"; }
deploy() { local t0; t0=$(now); "$BASH5" "$DEP" f1 > "$OUT/$1.log" 2>&1; echo "$2 rc=$? seconds=$(since "$t0")" | tee -a "$F"; }

"$BASH5" "$DEP" n1 > "$OUT/prev.log" 2>&1; echo "n1 rc=$?" | tee -a "$F"
q -e "CREATE TABLE lt_fail_probe (id BIGINT PRIMARY KEY)"
deploy bad "f1 첫 배포"
grep -hE '되돌리기|되돌린다|다시 시작했다' "$OUT/bad.log" | cut -c1-120 | tee -a "$F"
curl -s -o /dev/null -w 'service after rollback %{http_code}\n' http://127.0.0.1:28081/api/programs | tee -a "$F"
echo "--- 1단계: 실패 행" | tee -a "$F"
docq "SELECT installed_rank, version, description, success FROM flyway_schema_history WHERE success = 0" 2>&1 | tee -a "$F"
echo "--- 고치기 전에 같은 태그" | tee -a "$F"
deploy bad2 "f1 수리 전 재배포"
echo "--- 2단계: 문장마다 반영 확인 후 되돌림" | tee -a "$F"
echo "index idx_fail_probe_a=$(count statistics "index_name='idx_fail_probe_a'") table lt_fail_probe=$(count tables "table_name='lt_fail_probe'")" | tee -a "$F"
q -e "DROP INDEX idx_fail_probe_a ON community_post; DROP TABLE lt_fail_probe"
echo "--- 3단계: 실패 행 지우기" | tee -a "$F"
docq "DELETE FROM flyway_schema_history WHERE success = 0; SELECT ROW_COUNT() AS deleted" 2>&1 | tee -a "$F"
echo "--- 4단계: 같은 태그" | tee -a "$F"
deploy good "f1 수리 뒤 재배포"
q -N -e "SELECT version, success FROM flyway_schema_history WHERE version='999'" | sed 's/^/history /' | tee -a "$F"
echo "index=$(count statistics "index_name='idx_fail_probe_a'") table=$(count tables "table_name='lt_fail_probe'")" | tee -a "$F"
curl -s -o /dev/null -w 'service after fix %{http_code}\n' http://127.0.0.1:28081/api/programs | tee -a "$F"
q -e "DROP INDEX idx_fail_probe_a ON community_post; DROP TABLE lt_fail_probe; DELETE FROM flyway_schema_history WHERE version='999'"
"$BASH5" "$DEP" n1 > "$OUT/end.log" 2>&1; echo "n1 rc=$?" | tee -a "$F"
