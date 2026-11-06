#!/usr/bin/env bash
# 측정 틀을 만든다. opt/에 운영 파일(compose, Caddyfile, config.alloy, deploy.sh)과 가짜 비밀값을 두고, 배포할 이미지에 태그를 붙인다.
# 사용: setup.sh <앱 이미지>  예) setup.sh moewadeul-lt-app:dev-b9998c3c
# 앱 이미지는 backend/에서 ./gradlew bootJar 뒤 docker build로 만든다. Dockerfile이 build/libs의 jar를 그대로 복사하므로 bootJar를 빼면 옛 jar가 들어간다.
# 붙이는 태그: n1, n2(평소 배포), k1(시작 20초 뒤 죽는 버전), m1(sql/long의 V999), f1(sql/fail의 V999). 관리 포트 전 버전 o1은 직접 붙인다.
set -euo pipefail
source "$(dirname "$0")/env.sh"
BASE="${1:?앱 이미지}"
docker image inspect "$BASE" > /dev/null
if [[ ! -d "$OPT/.secrets" ]]; then
    mkdir -p "$OPT"/{.secrets,caddy,alloy,web,metrics,data,mysql-log,backup,sink}
    for n in db__password db__root-password publicdata__service-key mail__app-password identity__ci-hmac-key \
        member__mail-secret-hmac-key openai__api-key db__exporter-password grafana__token; do
        openssl rand -hex 24 > "$OPT/.secrets/$n"
    done
    # SITE_ADDRESS=:80은 Caddy가 인증서 없이 모든 호스트를 받게 한다. Grafana 주소는 Alloy를 띄우지 않으므로 형식만 맞춘다.
    printf '%s\n' SITE_ADDRESS=:80 DB_NAME=moewadeul DB_USERNAME=moewadeul MAIL_USERNAME=lt@loadtest.invalid \
        IDENTITY_FAKE_PROVIDER_ENABLED=true UPLOAD_BUCKET=lt-fake GRAFANA_PROM_URL=http://unused.invalid GRAFANA_PROM_USER=0 \
        GRAFANA_LOKI_URL=http://unused.invalid GRAFANA_LOKI_USER=0 > "$OPT/server.env"
fi
cp "$ROOT/deploy/docker-compose.prod.yml" "$OPT/docker-compose.prod.yml"
cp "$ROOT/deploy/caddy/Caddyfile" "$OPT/caddy/Caddyfile"
cp "$ROOT/deploy/alloy/config.alloy" "$OPT/alloy/config.alloy"
cp "$S/override.yml" "$OPT/override.yml"
cp "$S/sink.py" "$OPT/sink/sink.py"
git -C "$ROOT" rev-parse --short=8 HEAD > "$OPT/source-commit"
"$S/mkdeploy.sh"

docker tag "$BASE" moewadeul-lt-app:n1
docker tag "$BASE" moewadeul-lt-app:n2
# 셸이 PID 1이면 SIGTERM을 무시한다. trap이 없으면 되돌리기에서 docker stop이 sleep이 끝날 때까지 기다린다.
printf '%s\n' "FROM $BASE" 'ENTRYPOINT ["sh","-c","trap \"exit 143\" TERM; sleep 20 & wait $!; echo crash-late; exit 1"]' \
    | docker build -q -t moewadeul-lt-app:k1 - > /dev/null
for pair in m1:long f1:fail; do
    printf '%s\n' "FROM $BASE" "COPY ${pair#*:} /extra" 'ENV SPRING_FLYWAY_LOCATIONS=classpath:db/migration,filesystem:/extra' \
        | docker build -q -t "moewadeul-lt-app:${pair%%:*}" -f - "$S/sql" > /dev/null
done
for t in n1 n2 k1 m1 f1 o1; do
    docker image inspect "moewadeul-lt-app:$t" > /dev/null 2>&1 || continue
    printf '%s\n' 'FROM caddy:2.11.7-alpine' "RUN mkdir -p /srv/assets && echo '<!doctype html>$t' > /srv/index.html" \
        | docker build -q -t "moewadeul-lt-caddy:$t" - > /dev/null
done
echo "준비 끝: $OPT ($(<"$OPT/source-commit"))"
