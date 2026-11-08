#!/usr/bin/env bash
# 결과 폴더(SWEEP_DIR)를 처음 한 번 채운다. 측정 전용 가짜 비밀값을 새로 만들고, Caddy 설정과 공공데이터 고정 파일을 복사한다.
set -euo pipefail
. "$(dirname "$0")/env.sh"
mkdir -p "$D"/{caddy,web,data,mysql-log,sink,out,logs,ab}
chmod 777 "$D/mysql-log" "$D/sink"
if [ ! -f "$D/opt.env" ]; then
  r() { openssl rand -hex 16; }
  cat > "$D/opt.env" <<ENV
# 측정 전용 가짜 값이다. 실제 키가 아니다.
SITE_ADDRESS=':80'
UPLOAD_BUCKET='ltbucket'
DB_NAME='moewadeul'
DB_USERNAME='moewadeul'
DB_PASSWORD='$(r)'
DB_ROOT_PASSWORD='$(r)'
MAIL_USERNAME='lt@example.invalid'
MAIL_APP_PASSWORD='$(r)'
OPENAI_API_KEY='$(r)'
PUBLICDATA_SERVICE_KEY='$(r)'
MYSQL_EXPORTER_PASSWORD='$(r)'
CI_HMAC_KEY='$(r)$(r)'
MAIL_SECRET_HMAC_KEY='$(r)$(r)'
GRAFANA_PROM_URL='http://unused'
GRAFANA_PROM_USER='u'
GRAFANA_LOKI_URL='http://unused'
GRAFANA_LOKI_USER='u'
GRAFANA_CLOUD_TOKEN='unused'
ENV
fi
sed 's#^{\$SITE_ADDRESS}#{$SITE_ADDRESS}#' "$ROOT/deploy/caddy/Caddyfile" > "$D/caddy/Caddyfile"
[ -f "$D/web/index.html" ] || echo '<!doctype html><title>sweep</title>' > "$D/web/index.html"
mkdir -p "$D/web/current" && cp "$D/web/index.html" "$D/web/current/index.html"
F="$ROOT/backend/src/test/resources/fixtures/publicdata"
cp "$F/forest/forest_20260325.csv" "$F/park-boundary/parks_20260630.tsv" "$F/forest-standard/forest-standard.json" "$D/data/"
cp "$F/gocamping/based-sync-list.json" "$S/stub/__files/gocamping.json"
echo "결과 폴더 준비: $D"
