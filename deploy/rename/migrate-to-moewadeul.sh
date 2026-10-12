#!/usr/bin/env bash
# 일회용: 운영 서버의 자원 이름을 pitchmap에서 moewadeul로 옮긴다. 점검 시간에 개발자가 서버에서 root로 실행한다.
#
# 사용법: migrate-to-moewadeul.sh [--check]
#   --check  준비가 끝났는지만 확인하고 아무것도 바꾸지 않는다
#
# 하는 일
#   1. 옛 스택(compose 프로젝트 pitchmap)의 컨테이너를 멈추고, 옛 백업 타이머를 끈다.
#   2. /opt/pitchmap의 server.env, data, metrics, last-successful-tag를 /opt/moewadeul로 복사한다.
#      server.env의 도메인, DB 이름·계정, 버킷 이름은 새 이름으로 바꿔 쓴다.
#   3. 볼륨 네 개를 pitchmap_*에서 moewadeul_*로 복사한다.
#   4. 새 MySQL 볼륨에서 임시 MySQL을 띄워 DB pitchmap을 moewadeul로 옮긴다.
#      트리거와 프로시저의 정의자(DEFINER)도 새 앱 계정으로 바꾼다. 정의자 계정이 없으면 트리거가 실패하기 때문이다.
#
# 옛 볼륨, /opt/pitchmap, 옛 볼륨 안의 DB는 지우지 않는다. 새 스택에 문제가 생기면 옛 컨테이너를 다시 켜서 되돌리기 위해서다.
# 다시 실행해도 된다. 이미 끝난 단계는 건너뛴다.
# 비밀번호는 명령 인자로 넘기지 않는다. MYSQL_PWD 환경 변수와 표준 입력으로만 넘겨 프로세스 목록에 남지 않게 한다.
set -euo pipefail
export PATH="$PATH:/snap/bin"

readonly OLD=pitchmap
readonly NEW=moewadeul
readonly OLD_DIR=/opt/pitchmap
readonly NEW_DIR=/opt/moewadeul
readonly REGION=ap-northeast-2
readonly NEW_PARAM_PREFIX=/moewadeul/prod
# deploy.sh가 배포할 때 읽는 비밀값이다. 하나라도 새 경로에 없으면 배포가 실패한다.
readonly REQUIRED_PARAMS=(db/password db/root-password publicdata/service-key mail/app-password identity/ci-hmac-key
    db/exporter-password grafana/token)
readonly VOLUMES=(mysql-data caddy-data caddy-config alloy-data)
readonly COPY_ITEMS=(data metrics last-successful-tag)
readonly RENAMED_ENV_KEYS=(SITE_ADDRESS DB_NAME DB_USERNAME BACKUP_BUCKET UPLOAD_BUCKET)
readonly MYSQL_IMAGE=mysql:8.4
readonly COPY_IMAGE=alpine:3.20
readonly TEMP_MYSQL=moewadeul-rename-mysql

log() {
    echo "[rename $(date -u +%H:%M:%S)] $*"
}

fail() {
    log "$*"
    exit 1
}

param() {
    aws ssm get-parameter --region "$REGION" --name "$NEW_PARAM_PREFIX/$1" \
        --with-decryption --query Parameter.Value --output text
}

volume_exists() {
    docker volume inspect "$1" > /dev/null 2>&1
}

# 옛 server.env에서 값 하나를 읽는다. 새 버킷 이름을 확인할 때 쓴다.
old_env_value() {
    local line
    line="$(grep -m1 "^$1=" "$OLD_DIR/server.env" || true)"
    echo "${line#*=}"
}

check() {
    [[ $EUID -eq 0 ]] || fail "root로 실행한다"
    [[ -f "$OLD_DIR/server.env" || -f "$NEW_DIR/server.env" ]] || fail "$OLD_DIR/server.env 가 없다"
    volume_exists "${OLD}_mysql-data" || volume_exists "${NEW}_mysql-data" || fail "MySQL 볼륨이 없다"

    local p
    for p in "${REQUIRED_PARAMS[@]}"; do
        if ! param "$p" > /dev/null; then
            fail "Parameter Store에서 $NEW_PARAM_PREFIX/$p 를 읽지 못했다. 값을 새 경로로 복사했는지, 인스턴스 역할 정책에 새 경로가 있는지 확인한다"
        fi
    done

    local backup_bucket
    backup_bucket="$(old_env_value BACKUP_BUCKET)"
    backup_bucket="${backup_bucket/#$OLD-/$NEW-}"
    if [[ -n "$backup_bucket" ]] && ! aws s3 ls "s3://$backup_bucket/mysql/" --region "$REGION" > /dev/null; then
        fail "새 백업 버킷 s3://$backup_bucket/mysql/ 을 읽지 못했다. 버킷과 인스턴스 역할 정책을 확인한다"
    fi
    log "확인 완료: root, server.env, MySQL 볼륨, Parameter Store ${#REQUIRED_PARAMS[@]}개, 새 백업 버킷"
}

stop_old_stack() {
    local running
    running="$(docker ps -q --filter "label=com.docker.compose.project=$OLD")"
    if [[ -n "$running" ]]; then
        # shellcheck disable=SC2086 # 컨테이너 ID 목록을 낱개 인자로 넘긴다
        docker stop $running > /dev/null
        log "1/4 옛 스택을 멈췄다(컨테이너 $(wc -w <<< "$running")개). 컨테이너와 볼륨은 지우지 않는다"
    else
        log "1/4 실행 중인 옛 스택이 없다"
    fi
    if systemctl is-enabled --quiet "$OLD-backup.timer" 2> /dev/null; then
        systemctl disable --now "$OLD-backup.timer" > /dev/null 2>&1
        log "1/4 옛 백업 타이머를 껐다"
    fi
}

# 바꿀 키의 값에서 옛 이름을 새 이름으로 바꾼다. 예: pitchmap.duckdns.org → moewadeul.duckdns.org,
# pitchmap-backup-<계정 ID> → moewadeul-backup-<계정 ID>. 다른 키는 그대로 둔다.
write_new_server_env() {
    local line key value renamed tmp
    tmp="$(umask 077 && mktemp "$NEW_DIR/.server.env.XXXXXX")"
    while IFS= read -r line || [[ -n "$line" ]]; do
        key="${line%%=*}"
        value="${line#*=}"
        renamed=false
        if [[ "$line" == *=* && "$line" != \#* ]]; then
            local k
            for k in "${RENAMED_ENV_KEYS[@]}"; do
                [[ "$key" == "$k" ]] && renamed=true
            done
        fi
        if $renamed; then
            echo "$key=${value//$OLD/$NEW}"
        else
            echo "$line"
        fi
    done < "$OLD_DIR/server.env" > "$tmp"
    chmod 600 "$tmp"
    mv "$tmp" "$NEW_DIR/server.env"
}

copy_deploy_dir() {
    mkdir -p "$NEW_DIR"
    if [[ -f "$NEW_DIR/server.env" ]]; then
        log "2/4 $NEW_DIR/server.env 가 이미 있다. 그대로 둔다"
    else
        write_new_server_env
        log "2/4 $NEW_DIR/server.env 를 썼다"
    fi
    local key
    for key in "${RENAMED_ENV_KEYS[@]}"; do
        log "    $(grep -m1 "^$key=" "$NEW_DIR/server.env" || echo "$key 없음")"
    done
    local item
    for item in "${COPY_ITEMS[@]}"; do
        if [[ -e "$NEW_DIR/$item" ]]; then
            log "2/4 $NEW_DIR/$item 이 이미 있다"
        elif [[ -e "$OLD_DIR/$item" ]]; then
            cp -a "$OLD_DIR/$item" "$NEW_DIR/$item"
            log "2/4 $item 을 복사했다"
        fi
    done
}

copy_volumes() {
    local name src dst
    for name in "${VOLUMES[@]}"; do
        src="${OLD}_$name"
        dst="${NEW}_$name"
        if volume_exists "$dst"; then
            log "3/4 $dst 가 이미 있다"
            continue
        fi
        if ! volume_exists "$src"; then
            log "3/4 $src 가 없어 건너뛴다"
            continue
        fi
        # compose가 자기가 만든 볼륨으로 알아보도록 라벨을 붙여 만든다.
        docker volume create --label "com.docker.compose.project=$NEW" --label "com.docker.compose.volume=$name" \
            "$dst" > /dev/null
        docker run --rm -v "$src:/from:ro" -v "$dst:/to" "$COPY_IMAGE" sh -c 'cp -a /from/. /to/'
        log "3/4 $src → $dst 복사"
    done
}

temp_mysql() {
    docker exec -i -e MYSQL_PWD "$TEMP_MYSQL" mysql -uroot --batch --skip-column-names "$@"
}

stop_temp_mysql() {
    docker rm -f "$TEMP_MYSQL" > /dev/null 2>&1 || true
}

start_temp_mysql() {
    stop_temp_mysql
    # 운영 compose와 같은 이미지와 옵션으로 띄운다. 네트워크는 붙이지 않는다.
    docker run -d --name "$TEMP_MYSQL" --network none -v "${NEW}_mysql-data:/var/lib/mysql" "$MYSQL_IMAGE" \
        --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --default-time-zone=+00:00 \
        --log-bin-trust-function-creators=1 > /dev/null
    local _
    for _ in $(seq 1 60); do
        if docker exec -e MYSQL_PWD "$TEMP_MYSQL" mysqladmin ping -h 127.0.0.1 -uroot --silent > /dev/null 2>&1; then
            return
        fi
        sleep 2
    done
    fail "임시 MySQL이 2분 안에 뜨지 않았다. docker logs $TEMP_MYSQL 로 원인을 본다"
}

schema_exists() {
    [[ "$(temp_mysql -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$1'")" == 1 ]]
}

# 테이블마다 행 수를 "테이블 행 수" 줄로 출력한다. 옛 DB와 새 DB를 비교할 때 쓴다.
row_counts() {
    local schema="$1" table
    for table in $(temp_mysql -e "SELECT TABLE_NAME FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = '$schema' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME"); do
        echo "$table $(temp_mysql "$schema" -e "SELECT COUNT(*) FROM \`$table\`")"
    done
}

definer_mismatches() {
    temp_mysql -e "SELECT
        (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = '$NEW' AND DEFINER <> '$NEW@%')
      + (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = '$NEW' AND DEFINER <> '$NEW@%')"
}

routine_count() {
    temp_mysql -e "SELECT
        (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = '$1')
      + (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = '$1')"
}

last_migration() {
    temp_mysql "$1" -e 'SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1'
}

rename_database() {
    if [[ -n "$(docker ps -q --filter "volume=${NEW}_mysql-data")" ]]; then
        if [[ -n "$(docker ps -q --filter "name=^${TEMP_MYSQL}$")" ]]; then
            stop_temp_mysql
        else
            fail "4/4 다른 컨테이너가 ${NEW}_mysql-data를 쓰고 있다. 새 스택이 이미 떠 있으면 이 단계를 다시 할 필요가 없다"
        fi
    fi
    local root_password app_password
    root_password="$(param db/root-password)"
    app_password="$(param db/password)"
    if [[ "$app_password" == *"'"* || "$app_password" == *\\* ]]; then
        fail "4/4 앱 DB 비밀번호에 작은따옴표나 역슬래시가 있어 SQL로 넘길 수 없다"
    fi
    export MYSQL_PWD="$root_password"
    trap stop_temp_mysql EXIT
    start_temp_mysql

    if schema_exists "$NEW" && ! schema_exists "$OLD"; then
        log "4/4 DB가 이미 $NEW 로 옮겨져 있다. 확인만 한다"
        verify_database
        stop_temp_mysql
        return
    fi
    schema_exists "$OLD" || fail "4/4 DB $OLD 도 $NEW 도 없다"
    if schema_exists "$NEW"; then
        # 지난 실행이 옮기는 도중에 멈췄다. 옛 DB가 남아 있으므로 새 DB를 지우고 처음부터 다시 옮긴다.
        log "4/4 반쯤 옮겨진 DB $NEW 를 지우고 다시 옮긴다"
        temp_mysql -e "DROP DATABASE \`$NEW\`"
    fi

    local old_counts old_routines old_migration
    old_counts="$(row_counts "$OLD")"
    old_routines="$(routine_count "$OLD")"
    old_migration="$(last_migration "$OLD")"

    log "4/4 DB $OLD → $NEW 복사(트리거·프로시저 정의자를 $NEW@% 로 바꾼다)"
    temp_mysql -e "CREATE DATABASE \`$NEW\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
    temp_mysql <<SQL
CREATE USER IF NOT EXISTS '$NEW'@'%' IDENTIFIED BY '$app_password';
ALTER USER '$NEW'@'%' IDENTIFIED BY '$app_password';
GRANT ALL PRIVILEGES ON \`$NEW\`.* TO '$NEW'@'%';
SQL
    # 공간 열 같은 바이너리 값은 --hex-blob으로 16진수 글자로 덤프한다. sed가 글자만 다루게 하기 위해서다.
    docker exec -e MYSQL_PWD "$TEMP_MYSQL" mysqldump -uroot --single-transaction --routines --triggers --events \
        --set-gtid-purged=OFF --no-tablespaces --hex-blob "$OLD" \
        | LC_ALL=C sed "s/\`$OLD\`@\`%\`/\`$NEW\`@\`%\`/g" \
        | temp_mysql "$NEW"

    [[ "$(row_counts "$NEW")" == "$old_counts" ]] || fail "4/4 테이블 행 수가 옛 DB와 다르다. 옛 DB는 그대로 남아 있다"
    [[ "$(routine_count "$NEW")" == "$old_routines" ]] || fail "4/4 트리거·프로시저 수가 옛 DB와 다르다"
    [[ "$(last_migration "$NEW")" == "$old_migration" ]] || fail "4/4 마지막 마이그레이션 번호가 옛 DB와 다르다"
    verify_database

    # 새 볼륨 안의 옛 DB와 옛 계정만 지운다. 옛 볼륨(${OLD}_mysql-data)에는 그대로 남아 있다.
    temp_mysql <<SQL
DROP DATABASE \`$OLD\`;
DROP USER IF EXISTS '$OLD'@'%';
DROP USER IF EXISTS '${OLD}_exporter'@'%';
SQL
    log "4/4 새 볼륨에서 옛 DB와 옛 계정을 지웠다"
    stop_temp_mysql
}

verify_database() {
    [[ "$(definer_mismatches)" == 0 ]] || fail "4/4 정의자가 $NEW@% 가 아닌 트리거·프로시저가 있다"
    local app_password
    app_password="$(param db/password)"
    MYSQL_PWD="$app_password" docker exec -e MYSQL_PWD "$TEMP_MYSQL" \
        mysql -u"$NEW" -h 127.0.0.1 --batch --skip-column-names "$NEW" -e 'SELECT 1' > /dev/null \
        || fail "4/4 앱 계정 $NEW 로 DB에 접속하지 못했다"
    log "4/4 확인: 테이블 $(row_counts "$NEW" | wc -l)개, 트리거·프로시저 $(routine_count "$NEW")개(정의자 모두 $NEW@%), 마지막 마이그레이션 V$(last_migration "$NEW"), 앱 계정 접속 성공"
}

main() {
    check
    if [[ "${1:-}" == "--check" ]]; then
        exit 0
    fi
    stop_old_stack
    copy_deploy_dir
    copy_volumes
    rename_database
    log "끝. 다음 순서: 업로드 버킷 마지막 동기화 → 릴리즈 PR 머지(배포가 $NEW_DIR 에서 새 스택을 띄운다)"
    log "되돌리려면: docker start \$(docker ps -aq --filter label=com.docker.compose.project=$OLD) && systemctl enable --now $OLD-backup.timer"
}

main "$@"
