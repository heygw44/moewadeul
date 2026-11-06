# shellcheck shell=bash disable=SC2034
# 배포 측정 도구가 함께 읽는 경로와 함수. source해서 쓴다.
S="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$S/../../.." && pwd)"
D="${DEPLOY_LT_DIR:-$ROOT/backend/load-test/results/deploy}"
OPT="$D/opt"
P="${DEPLOY_LT_PROJECT:-moewadeul-lt-dep}"
# deploy.sh는 연관 배열을 써서 bash 4 이상이 필요하다. macOS 기본 bash는 3.2다.
BASH5="${BASH5:-/opt/homebrew/bin/bash}"
DC=(docker compose --project-directory "$OPT" -p "$P" -f "$OPT/docker-compose.prod.yml" -f "$OPT/override.yml" --env-file "$OPT/.env")
q() { MYSQL_PWD="$(<"$OPT/.secrets/db__root-password")" docker exec -i -e MYSQL_PWD "$P-mysql-1" mysql -uroot --default-character-set=utf8mb4 moewadeul "$@"; }
csrf=measure-csrf-token
