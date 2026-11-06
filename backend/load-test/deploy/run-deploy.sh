#!/usr/bin/env bash
# 사용: run-deploy.sh <실행 이름> <이전 태그> <새 태그>
# 이전 태그를 배포해 두고, 서버 밖에서 Caddy로 GET 2건/초와 댓글 POST 2건/초를 보내며 새 태그를 배포한다. 결과는 runs/<실행 이름>에 남는다.
# AFTER_SLEEP(기본 45초): 배포가 끝난 뒤 요청을 더 보내는 시간.
set -u
source "$(dirname "$0")/env.sh"
NAME=$1 PREV=$2 NEW=$3 DEP="$OPT/deploy.lt.sh" BASE=http://127.0.0.1:28081
OUT="$D/runs/$NAME"
[ -e "$OUT" ] && mv "$OUT" "$OUT.old.$(date +%s)"
mkdir -p "$OUT"
stamp() { python3 -u -c 'import sys,time
for l in sys.stdin: print(f"{time.time():.3f} {l}",end="",flush=True)'; }
now() { python3 -c 'import time;print(f"{time.time():.3f}")'; }
api() { curl -s -H 'Content-Type: application/json' -H "Cookie: XSRF-TOKEN=$csrf${SESSION:+; SESSION=$SESSION}" -H "X-XSRF-TOKEN: $csrf" "$@"; }
c502() { docker exec "$P-caddy-1" wget -qO- http://127.0.0.1:2020/metrics | awk '/^caddy_http_request_duration_seconds_count/ && /code="502"/ && /handler="subroute"/ {s+=$2} END {print s+0}'; }
mysql_health() { docker inspect -f '{{.State.Health.Status}} failing={{.State.Health.FailingStreak}}' "$P-mysql-1"; }

"$BASH5" "$DEP" "$PREV" > "$OUT/prev-deploy.log" 2>&1 || { echo "이전 태그 배포 실패"; tail "$OUT/prev-deploy.log"; exit 1; }
sleep 15
SESSION=$(curl -s -o /dev/null -D - -H 'Content-Type: application/json' -H "Cookie: XSRF-TOKEN=$csrf" -H "X-XSRF-TOKEN: $csrf" \
    -d '{"email":"user000001@loadtest.invalid","password":"LoadTest#2026"}' "$BASE/api/auth/login" \
    | sed -n 's/^[Ss]et-[Cc]ookie: SESSION=\([^;]*\).*/\1/p')
POST_ID=$(api -H "Idempotency-Key: dep-$NAME-$(date +%s)" -d '{"title":"배포 측정 글","content":"댓글을 받는 글"}' "$BASE/api/community/posts" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin)["postId"])')
{ echo "caddy_502_before $(c502)"; echo "mysql_health_before $(mysql_health)"; } > "$OUT/health.txt"
docker events --format '{{.TimeNano}} {{.Actor.Attributes.name}} {{.Action}}' --filter type=container \
    --filter "label=com.docker.compose.project=$P" > "$OUT/events.raw" 2>&1 &
EV=$!
k6 run -q --console-output="$OUT/k6-requests.log" -e BASE_URL=$BASE -e RUN_NAME="$NAME" -e COMMENT_EMAIL=user000001@loadtest.invalid \
    -e COMMENT_POST_ID="$POST_ID" "$S/probe.js" > "$OUT/k6.log" 2>&1 &
K6=$!
until grep -q SETUP_DONE "$OUT/k6-requests.log" 2>/dev/null; do
    sleep 1
    kill -0 $K6 2>/dev/null || { echo "k6 setup 실패"; tail "$OUT/k6.log"; kill $EV; exit 1; }
done
sleep 20
echo "deploy_start $(now)" > "$OUT/timeline.txt"
"$BASH5" "$DEP" "$NEW" 2>&1 | stamp > "$OUT/deploy.log"
RC=${PIPESTATUS[0]}
echo "deploy_end $(now) rc=$RC" >> "$OUT/timeline.txt"
sleep "${AFTER_SLEEP:-45}"
kill -INT $K6; wait $K6 2>/dev/null; kill $EV 2>/dev/null
docker logs "$P-app-1" > "$OUT/app.log" 2>&1
{ echo "caddy_502_after $(c502)"; echo "mysql_health_after $(mysql_health)"; } >> "$OUT/health.txt"
rows=$(q -N -e "SELECT COUNT(*) FROM community_comment WHERE content LIKE '배포 측정 $NAME %'")
dups=$(q -N -e "SELECT COUNT(*) FROM (SELECT content FROM community_comment WHERE content LIKE '배포 측정 $NAME %' GROUP BY content HAVING COUNT(*)>1) d")
echo "prev=$PREV new=$NEW rc=$RC comment_rows=$rows duplicated_contents=$dups" > "$OUT/meta.txt"
{ python3 "$S/analyze.py" "$OUT"; cat "$OUT/meta.txt" "$OUT/health.txt"; } > "$OUT/summary.txt"
echo "done $NAME rc=$RC"
