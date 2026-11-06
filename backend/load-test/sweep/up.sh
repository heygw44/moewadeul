#!/usr/bin/env bash
# 사용: up.sh [debug]. 앱을 새로 띄우고 준비될 때까지 기다린다. debug는 SQL 로그를 켠다(대응표 만들 때만, 시간을 재지 않는다).
. "$(dirname "$0")/env.sh"
if [ "${1:-}" = debug ]; then export LT_APP_LOG=DEBUG LT_APP_JSON='{"logging":{"level":{"org.hibernate.SQL":"DEBUG","org.hibernate.orm.jdbc.bind":"TRACE"}},"spring":{"jpa":{"properties":{"hibernate.use_sql_comments":true}}}}'; fi
"$S/lt.sh" up -d --wait mysql extstub smtpsink 2>&1 | grep -iE 'error|failed|bind' 
"$S/lt.sh" up -d --force-recreate --no-deps app caddy 2>&1 | grep -iE 'error|failed|bind'
for i in $(seq 1 120); do [ "$(curl -s -m 2 -o /dev/null -w '%{http_code}' http://127.0.0.1:47181/actuator/health)" = 200 ] && { echo "app up after $((i*2))s"; exit 0; }; sleep 2; done
echo "app not healthy"; "$S/lt.sh" logs --tail 20 app | cut -c1-300; exit 1
