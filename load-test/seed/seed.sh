#!/usr/bin/env bash
# 부하 검증용 데이터를 빈 DB에 넣는다. 생성 규칙은 같은 폴더의 seed.sql 머리말과 load-test/README.md에 있다.
#
# 환경 변수
#   SPOT_COUNT    장소 수. 기본 1000000
#   MEMBER_COUNT  신청 회원 수(관리자 1명은 따로). 기본 2000, 최대 100000
#   SEED_DB       데이터를 넣을 데이터베이스 이름. 비우면 MySQL 컨테이너의 MYSQL_DATABASE
#   ENV_FILE      docker compose에 넘길 --env-file. 비우면 compose 기본값(.env)
#   COMPOSE_FILE  docker compose가 직접 읽는 표준 변수. 검증 서버에서는 운영 compose 파일을 가리킨다
#   SEED_STAGES   돌릴 단계. base(장소·신청 회원, seed.sql), extra(베이스캠프·커뮤니티 등, seed-extra.sql). 기본 base,extra.
#                 첫 단계를 이미 마친 DB에는 extra만 돌린다.
#   두 번째 단계 규모(기본값): EXTRA_MEMBER_COUNT 200000(배경 회원, 10000~1000000), BASECAMP_COUNT 300000, AUDIT_LOG_COUNT 100000,
#                 LOGIN_HISTORY_COUNT 3000000, PROGRAM_COUNT 2000, POST_COUNT 200000, MEMORY_COUNT 100000
#
# 이 스크립트는 DB 비밀번호를 받지 않는다. MySQL 컨테이너 안의 MYSQL_ROOT_PASSWORD로 접속하므로 비밀번호가 인자나 로그에 남지 않는다.
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SPOT_COUNT="${SPOT_COUNT:-1000000}"
readonly MEMBER_COUNT="${MEMBER_COUNT:-2000}"
readonly SEED_DB="${SEED_DB:-}"
readonly SEED_STAGES="${SEED_STAGES:-base,extra}"
readonly EXTRA_MEMBER_COUNT="${EXTRA_MEMBER_COUNT:-200000}"
readonly BASECAMP_COUNT="${BASECAMP_COUNT:-300000}"
readonly AUDIT_LOG_COUNT="${AUDIT_LOG_COUNT:-100000}"
readonly LOGIN_HISTORY_COUNT="${LOGIN_HISTORY_COUNT:-3000000}"
readonly PROGRAM_COUNT="${PROGRAM_COUNT:-2000}"
readonly POST_COUNT="${POST_COUNT:-200000}"
readonly MEMORY_COUNT="${MEMORY_COUNT:-100000}"

for value in "$SPOT_COUNT" "$MEMBER_COUNT" "$EXTRA_MEMBER_COUNT" "$BASECAMP_COUNT" "$AUDIT_LOG_COUNT" \
    "$LOGIN_HISTORY_COUNT" "$PROGRAM_COUNT" "$POST_COUNT" "$MEMORY_COUNT"; do
    if ! [[ "$value" =~ ^[0-9]+$ ]]; then
        echo "규모 환경 변수는 숫자여야 합니다: $value" >&2
        exit 1
    fi
done
if ! [[ "$SEED_STAGES" =~ ^(base|extra|base,extra)$ ]]; then
    echo "SEED_STAGES는 base, extra, base,extra 중 하나여야 합니다." >&2
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

run_sql() {
    docker compose ${compose_args[@]+"${compose_args[@]}"} exec -T -e SEED_DB="$SEED_DB" mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --default-character-set=utf8mb4 "${SEED_DB:-$MYSQL_DATABASE}"'
}

started=$(date +%s)

if [[ "$SEED_STAGES" == base* ]]; then
    echo "$(date '+%F %T') 첫 단계 시작: 장소 ${SPOT_COUNT}건, 신청 회원 ${MEMBER_COUNT}명, DB ${SEED_DB:-(컨테이너 기본값)}"
    {
        echo "SET @spot_count = ${SPOT_COUNT};"
        echo "SET @member_count = ${MEMBER_COUNT};"
        cat "$SCRIPT_DIR/seed.sql"
    } | run_sql
    echo "$(date '+%F %T') 첫 단계 끝: $(( $(date +%s) - started ))초"
fi

if [[ "$SEED_STAGES" == *extra ]]; then
    extra_started=$(date +%s)
    echo "$(date '+%F %T') 두 번째 단계 시작: 배경 회원 ${EXTRA_MEMBER_COUNT}명, 베이스캠프 ${BASECAMP_COUNT}개, 글 ${POST_COUNT}개," \
        "기록 ${MEMORY_COUNT}개, 로그인 기록 ${LOGIN_HISTORY_COUNT}건, 행사 ${PROGRAM_COUNT}개, 감사 로그 ${AUDIT_LOG_COUNT}건"
    {
        echo "SET @extra_member_count = ${EXTRA_MEMBER_COUNT};"
        echo "SET @basecamp_count = ${BASECAMP_COUNT};"
        echo "SET @audit_log_count = ${AUDIT_LOG_COUNT};"
        echo "SET @login_history_count = ${LOGIN_HISTORY_COUNT};"
        echo "SET @program_count = ${PROGRAM_COUNT};"
        echo "SET @post_count = ${POST_COUNT};"
        echo "SET @memory_count = ${MEMORY_COUNT};"
        cat "$SCRIPT_DIR/seed-extra.sql"
    } | run_sql
    echo "$(date '+%F %T') 두 번째 단계 끝: $(( $(date +%s) - extra_started ))초"
fi

echo "$(date '+%F %T') 끝: $(( $(date +%s) - started ))초"
