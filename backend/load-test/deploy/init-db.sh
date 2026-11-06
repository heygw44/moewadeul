#!/usr/bin/env bash
# 빈 MySQL에 n1을 처음 배포해 Flyway가 스키마를 만들게 하고, 작은 규모로 시드 두 단계(base, extra)를 넣는다.
set -euo pipefail
source "$(dirname "$0")/env.sh"
"$BASH5" "$OPT/deploy.lt.sh" n1 | tail -1
"${DC[@]}" up -d --no-deps smtpsink
cd "$ROOT/backend"
COMPOSE_PROJECT_NAME=$P COMPOSE_FILE="$OPT/docker-compose.prod.yml:$OPT/override.yml" ENV_FILE="$OPT/.env" \
    SPOT_COUNT=20000 MEMBER_COUNT=200 EXTRA_MEMBER_COUNT=10000 BASECAMP_COUNT=5000 AUDIT_LOG_COUNT=2000 \
    LOGIN_HISTORY_COUNT=20000 PROGRAM_COUNT=10 POST_COUNT=3000 MEMORY_COUNT=2000 load-test/seed/seed.sh | tail -1
