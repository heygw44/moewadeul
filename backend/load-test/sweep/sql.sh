#!/usr/bin/env bash
# 표준 입력의 SQL을 복사본 MySQL에서 root로 돌린다. 값은 측정 전용 가짜 값이다.
. "$(dirname "$0")/env.sh"
set -a; . "$D/opt.env"; set +a
exec docker exec -i -e MYSQL_PWD="$DB_ROOT_PASSWORD" moewadeul-lt-sweep-mysql-1 mysql -uroot --default-character-set=utf8mb4 "${@:---table}" ${SQL_DB:-moewadeul}
