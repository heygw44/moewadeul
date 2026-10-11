#!/usr/bin/env bash
# 부하 검증용 데이터를 빈 DB에 넣는다. 생성 규칙은 같은 폴더의 seed.sql 머리말과 load-test/README.md에 있다.
#
# 환경 변수
#   SPOT_COUNT    장소 수. 기본 1000000
#   MEMBER_COUNT  신청 회원 수(관리자 1명은 따로). 기본 2000, 최대 100000
#   SEED_DB       데이터를 넣을 데이터베이스 이름. 비우면 MySQL 컨테이너의 MYSQL_DATABASE
#   ENV_FILE      docker compose에 넘길 --env-file. 비우면 compose 기본값(.env)
#   COMPOSE_FILE  docker compose가 직접 읽는 표준 변수. 검증 서버에서는 운영 compose 파일을 가리킨다
#
# 이 스크립트는 DB 비밀번호를 받지 않는다. MySQL 컨테이너 안의 MYSQL_ROOT_PASSWORD로 접속하므로 비밀번호가 인자나 로그에 남지 않는다.
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SPOT_COUNT="${SPOT_COUNT:-1000000}"
readonly MEMBER_COUNT="${MEMBER_COUNT:-2000}"
readonly SEED_DB="${SEED_DB:-}"

if ! [[ "$SPOT_COUNT" =~ ^[0-9]+$ && "$MEMBER_COUNT" =~ ^[0-9]+$ ]]; then
    echo "SPOT_COUNT와 MEMBER_COUNT는 숫자여야 합니다." >&2
    exit 1
fi
if [[ -n "$SEED_DB" && ! "$SEED_DB" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "SEED_DB에는 영문, 숫자, 밑줄만 쓸 수 있습니다." >&2
    exit 1
fi

compose_args=()
if [[ -n "${ENV_FILE:-}" ]]; then
    compose_args+=(--env-file "$ENV_FILE")
fi

echo "$(date '+%F %T') 시작: 장소 ${SPOT_COUNT}건, 신청 회원 ${MEMBER_COUNT}명, DB ${SEED_DB:-(컨테이너 기본값)}"
started=$(date +%s)

{
    echo "SET @spot_count = ${SPOT_COUNT};"
    echo "SET @member_count = ${MEMBER_COUNT};"
    cat "$SCRIPT_DIR/seed.sql"
} | docker compose ${compose_args[@]+"${compose_args[@]}"} exec -T -e SEED_DB="$SEED_DB" mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --default-character-set=utf8mb4 "${SEED_DB:-$MYSQL_DATABASE}"'

echo "$(date '+%F %T') 끝: $(( $(date +%s) - started ))초"
