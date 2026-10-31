#!/usr/bin/env bash
# 운영 서버에서 이미지 태그 하나를 배포한다. 배포 워크플로가 SSM Run Command로 root 권한으로 실행한다.
#
# 사용법: deploy.sh <이미지 태그(커밋 SHA)>
# 종료 코드: 0 성공 / 1 실패(되돌릴 태그가 없거나 되돌리기도 실패) / 2 실패했지만 직전에 성공한 태그로 되돌림
#
# 호출자가 /run/lock/moewadeul.lock 잠금을 fd 9로 잡고 실행한다(배포 워크플로의 SSM 명령).
#
# 이 스크립트의 출력은 GitHub Actions 로그에 그대로 남고, 공개 저장소라 누구나 볼 수 있다.
# 그래서 비밀값을 출력하지 않고, 실패해도 앱 로그를 출력하지 않는다. Spring은 설정 바인딩에 실패하면
# 오류 메시지에 설정 값을 그대로 찍을 수 있다. 앱 로그는 서버에서 docker compose logs app으로 본다.
set -euo pipefail

# snap으로 설치한 AWS CLI는 /snap/bin에 있다. SSM이 여는 셸의 PATH에는 이 경로가 없을 수 있다.
export PATH="$PATH:/snap/bin"

readonly DEPLOY_DIR=/opt/moewadeul
readonly COMPOSE_FILE="$DEPLOY_DIR/docker-compose.prod.yml"
readonly ENV_FILE="$DEPLOY_DIR/.env"
readonly SERVER_ENV_FILE="$DEPLOY_DIR/server.env"
readonly LAST_TAG_FILE="$DEPLOY_DIR/last-successful-tag"
readonly LAST_COMPOSE_FILE="$DEPLOY_DIR/last-successful-compose.yml"
readonly CADDYFILE="$DEPLOY_DIR/caddy/Caddyfile"
readonly LAST_CADDYFILE="$DEPLOY_DIR/last-successful-Caddyfile"
readonly WEB_DIR="$DEPLOY_DIR/web"
readonly SERVER_COMPOSE_COMMAND="cd $DEPLOY_DIR && docker compose -f docker-compose.prod.yml"
readonly REGION=ap-northeast-2
readonly PARAM_PREFIX=/moewadeul/prod
readonly APP_REPO=ghcr.io/heygw44/moewadeul-app
readonly CADDY_REPO=ghcr.io/heygw44/moewadeul-caddy
readonly HEALTH_URL=http://127.0.0.1:8080/actuator/health
# 앱은 시작할 때 Flyway 마이그레이션을 실행하므로, 처음 배포하거나 마이그레이션이 많으면 시간이 걸린다.
readonly HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-240}"
# 시작하다 죽는 앱은 restart 정책으로 계속 다시 뜬다. 상태 확인 주소만 보면 그동안 제한 시간을 다 기다리고, 옛 앱은 이미 내려가 있어 502가 이어진다.
# 다시 시작한 횟수가 이보다 많으면 기다리지 않고 실패로 본다.
readonly MAX_APP_RESTARTS="${MAX_APP_RESTARTS:-2}"

# Parameter Store 이름(접두사 뒤) → .env 변수 이름
declare -rA SECRET_PARAMS=(
    [db/password]=DB_PASSWORD
    [db/root-password]=DB_ROOT_PASSWORD
    [publicdata/service-key]=PUBLICDATA_SERVICE_KEY
    [mail/app-password]=MAIL_APP_PASSWORD
    [identity/ci-hmac-key]=CI_HMAC_KEY
    [member/mail-secret-hmac-key]=MAIL_SECRET_HMAC_KEY
    [openai/api-key]=OPENAI_API_KEY
    [db/exporter-password]=MYSQL_EXPORTER_PASSWORD
    [grafana/token]=GRAFANA_CLOUD_TOKEN
)
readonly EXPORTER_USER=moewadeul_exporter
readonly BACKUP_DIR="$DEPLOY_DIR/backup"
readonly ALLOY_CONFIG_FILE="$DEPLOY_DIR/alloy/config.alloy"
readonly LAST_ALLOY_CONFIG_FILE="$DEPLOY_DIR/last-successful-config.alloy"
readonly ALLOY_CONTAINER_CONFIG=/etc/alloy/config.alloy
readonly BACKUP_METRICS_FILE="$DEPLOY_DIR/metrics/backup.prom"
readonly SYSTEMD_DIR=/etc/systemd/system
readonly MYSQL_SLOW_LOG_DIR="$DEPLOY_DIR/mysql-log"
readonly MYSQL_UID=999
readonly LOGROTATE_FILE=/etc/logrotate.d/moewadeul-mysql-slow

declare -rA LAST_SUCCESSFUL_FILES=(
    ["$COMPOSE_FILE"]="$LAST_COMPOSE_FILE"
    ["$CADDYFILE"]="$LAST_CADDYFILE"
    ["$ALLOY_CONFIG_FILE"]="$LAST_ALLOY_CONFIG_FILE"
)

declare -A secrets=()
declare -A server_env=()

log() {
    echo "[deploy $(date -u +%H:%M:%S)] $*"
}

# 배포 워크플로가 이 출력을 그대로 찍으므로, 줄 맨 앞의 ::warning::이 GitHub Actions 경고 주석이 된다.
warn() {
    echo "::warning::[deploy $(date -u +%H:%M:%S)] $*"
}

compose() {
    docker compose --project-directory "$DEPLOY_DIR" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

# .env는 값을 작은따옴표로 감싸 쓴다. Compose는 작은따옴표 안의 $를 변수로 해석하지 않는다.
# 대신 값에 작은따옴표나 줄바꿈이 있으면 파일이 깨지므로 거부한다.
require_safe_value() {
    local name="$1" value="$2"
    if [[ "$value" == *"'"* || "$value" == *$'\n'* ]]; then
        log "$name 값에 작은따옴표나 줄바꿈이 있어 .env에 쓸 수 없다"
        exit 1
    fi
}

save_last_successful_files() {
    local file saved
    for file in "${!LAST_SUCCESSFUL_FILES[@]}"; do
        saved="${LAST_SUCCESSFUL_FILES[$file]}"
        if ! { cp "$file" "$saved.tmp" && mv "$saved.tmp" "$saved"; }; then
            warn "배포 파일을 보관하지 못했다: $file. 다음 배포가 실패하면 보관된 옛 파일로 되돌린다"
        fi
    done
}

restore_last_successful_files() {
    local file saved
    for file in "${!LAST_SUCCESSFUL_FILES[@]}"; do
        saved="${LAST_SUCCESSFUL_FILES[$file]}"
        if [[ ! -f "$saved" ]]; then
            log "보관된 파일이 없어 새 파일 그대로 둔다: $file"
        elif cp "$saved" "$file"; then
            log "직전에 성공한 파일로 되돌렸다: $file"
        else
            warn "직전에 성공한 파일을 복사하지 못해 새 파일 그대로 둔다: $file"
        fi
    done
}

check_architecture() {
    local arch
    arch="$(uname -m)"
    if [[ "$arch" != "aarch64" ]]; then
        log "서버 아키텍처가 ${arch}다. 이미지는 arm64로만 빌드한다"
        exit 1
    fi
}

load_server_env() {
    if [[ ! -f "$SERVER_ENV_FILE" ]]; then
        log "$SERVER_ENV_FILE 이 없다. deploy/server.env.example을 보고 만든다"
        exit 1
    fi
    local line key value
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ -z "${line//[[:space:]]/}" || "$line" == \#* ]] && continue
        key="${line%%=*}"
        value="${line#*=}"
        if [[ ! "$key" =~ ^[A-Z_][A-Z0-9_]*$ || "$line" != *=* ]]; then
            log "server.env에 형식이 맞지 않는 줄이 있다: 변수 이름=값 형식으로 쓴다"
            exit 1
        fi
        require_safe_value "$key" "$value"
        server_env["$key"]="$value"
    done < "$SERVER_ENV_FILE"
    if [[ -z "${server_env[SITE_ADDRESS]:-}" ]]; then
        log "server.env에 SITE_ADDRESS가 없다"
        exit 1
    fi
}

load_secrets() {
    local param name value
    for param in "${!SECRET_PARAMS[@]}"; do
        name="${SECRET_PARAMS[$param]}"
        if ! value="$(aws ssm get-parameter --region "$REGION" --name "$PARAM_PREFIX/$param" \
            --with-decryption --query Parameter.Value --output text)"; then
            log "Parameter Store에서 $PARAM_PREFIX/$param 을 읽지 못했다"
            exit 1
        fi
        require_safe_value "$name" "$value"
        secrets["$name"]="$value"
    done
}

# 태그에 맞는 이미지 이름과 비밀값, 서버 설정을 합쳐 .env를 새로 쓴다.
# 임시 파일에 다 쓴 뒤 이름을 바꿔서, 쓰다가 멈춰도 반쯤 쓴 .env가 남지 않게 한다.
write_env() {
    local tag="$1" tmp key
    tmp="$(umask 077 && mktemp "$DEPLOY_DIR/.env.XXXXXX")"
    {
        echo "# deploy.sh가 배포할 때마다 새로 쓴다. 직접 고치지 않는다."
        echo "APP_IMAGE='$APP_REPO:$tag'"
        echo "CADDY_IMAGE='$CADDY_REPO:$tag'"
        for key in "${!server_env[@]}"; do
            printf "%s='%s'\n" "$key" "${server_env[$key]}"
        done
        for key in "${!secrets[@]}"; do
            printf "%s='%s'\n" "$key" "${secrets[$key]}"
        done
    } > "$tmp"
    chmod 600 "$tmp"
    mv "$tmp" "$ENV_FILE"
}

check_image_architecture() {
    local tag="$1" image arch
    for image in "$APP_REPO:$tag" "$CADDY_REPO:$tag"; do
        if ! arch="$(docker image inspect --format '{{.Architecture}}' "$image" 2>/dev/null)"; then
            log "$image 이미지가 서버에 없다"
            return 1
        fi
        if [[ "$arch" != "arm64" ]]; then
            log "$image 의 아키텍처가 ${arch}다"
            return 1
        fi
    done
}

# caddy 서비스 이미지는 서버에 없을 때만 받는다. 업스트림은 같은 태그를 다시 빌드하기도 해서, 매번 받으면 그날 배포가 Caddy를 다시 만든다.
pull_images() {
    local tag="$1"
    compose pull --quiet app && compose pull --quiet --policy missing caddy && docker pull --quiet "$CADDY_REPO:$tag" > /dev/null
}

app_restart_count() {
    local id
    id="$(compose ps -a -q app 2> /dev/null)" && [[ -n "$id" ]] || return 1
    docker inspect -f '{{.RestartCount}}' "$id" 2> /dev/null
}

wait_app_healthy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS)) restarts
    until curl -fsS -o /dev/null --max-time 5 "$HEALTH_URL" 2>/dev/null; do
        if restarts="$(app_restart_count)" && ((restarts > MAX_APP_RESTARTS)); then
            log "앱이 시작하다 ${restarts}번 다시 시작했다"
            return 1
        fi
        if ((SECONDS >= deadline)); then
            log "앱이 ${HEALTH_TIMEOUT_SECONDS}초 안에 정상 상태가 되지 않았다"
            return 1
        fi
        sleep 5
    done
    log "앱 상태 확인 통과"
}

# caddy는 80번 요청에 HTTPS로 넘기는 응답(308)을 준다. 인증서 발급은 Let's Encrypt 상황에 따라 늦어질 수 있어서 HTTPS는 확인하지 않는다.
wait_caddy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS)) code
    until code="$(curl -s -o /dev/null --max-time 5 -w '%{http_code}' \
        -H "Host: ${server_env[SITE_ADDRESS]}" http://127.0.0.1/)" && [[ "$code" =~ ^[23] ]]; do
        if ((SECONDS >= deadline)); then
            log "caddy가 응답하지 않는다"
            return 1
        fi
        sleep 2
    done
    log "caddy 응답 확인 통과"
}

# 이미지에 실행할 명령이 없어서 docker create에 아무 명령(true)이나 넘긴다. create는 컨테이너를 만들기만 하고 실행하지 않는다.
# current는 상대 경로 링크다. 컨테이너에서는 web이 /srv에 마운트되므로 /srv/current가 /srv/releases/<태그>로 풀린다.
publish_web() {
    local tag="$1" release="$WEB_DIR/releases/$1" id
    mkdir -p "$WEB_DIR/releases" || return 1
    if [[ ! -d "$release" ]]; then
        rm -rf "$release.tmp"
        id="$(docker create "$CADDY_REPO:$tag" true)" || { log "화면 파일을 꺼낼 컨테이너를 만들지 못했다"; return 1; }
        if ! docker cp "$id:/srv" "$release.tmp" > /dev/null; then
            docker rm "$id" > /dev/null 2>&1 || true
            log "$CADDY_REPO:$tag 에서 화면 파일을 꺼내지 못했다"
            return 1
        fi
        docker rm "$id" > /dev/null 2>&1 || warn "화면 파일을 꺼내려고 만든 컨테이너 $id 를 지우지 못했다"
        mv "$release.tmp" "$release" || return 1
    fi
    [[ -f "$release/index.html" ]] || { log "$tag 화면 파일에 index.html이 없다"; return 1; }
    if ! { ln -sfn "releases/$tag" "$WEB_DIR/current.tmp" && mv -T "$WEB_DIR/current.tmp" "$WEB_DIR/current"; }; then
        log "web/current를 $tag 로 바꾸지 못했다"
        return 1
    fi
    prune_web_releases "$tag" || warn "오래된 화면 파일을 지우지 못했다. 배포는 계속한다"
    log "화면 파일 전환: $tag"
}

prune_web_releases() {
    local keep="$1" previous="" dir
    [[ -f "$LAST_TAG_FILE" ]] && previous="$(<"$LAST_TAG_FILE")"
    for dir in "$WEB_DIR"/releases/*; do
        [[ -e "$dir" ]] || continue
        case "${dir##*/}" in "$keep" | "$previous") continue ;; esac
        rm -rf "$dir" || return 1
    done
}

# Caddy를 막 다시 만든 직후(첫 배포 등)에는 관리 API가 아직 열리지 않아 reload가 실패할 수 있어 몇 번 다시 시도한다.
reload_caddy() {
    local attempt
    for attempt in 1 2 3 4 5; do
        compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile > /dev/null && return 0
        ((attempt < 5)) && sleep 2
    done
    log "caddy reload에 실패했다. Caddyfile이 잘못됐으면 옛 설정이 그대로 쓰인다"
    return 1
}

definition_changed() {
    local service="$1" id expected actual
    id="$(compose ps -q "$service" 2> /dev/null)" && [[ -n "$id" ]] || return 1
    expected="$(compose config --hash "$service" 2> /dev/null | cut -d' ' -f2)" || return 1
    actual="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.config-hash"}}' "$id" 2> /dev/null)" || return 1
    [[ -n "$expected" && "$expected" != "$actual" ]]
}

# 앱을 --no-deps로 올리면 compose가 depends_on의 service_healthy를 보지 않으므로 MySQL이 정상이 될 때까지 여기서 기다린다.
start_mysql_without_recreate() {
    compose up -d --no-recreate --wait mysql || return 1
    if definition_changed mysql; then
        warn "mysql 정의가 실행 중인 컨테이너와 달라도 배포는 적용하지 않았다. 이 정의는 이번 배포에서 검증되지 않았다. 백업을 확인한 뒤 서버에서 '$SERVER_COMPOSE_COMMAND up -d mysql'로 적용한다"
    fi
}

# if 조건 안에서 부르면 set -e가 꺼지므로, 단계마다 &&로 이어 앞 단계가 실패하면 바로 실패를 돌려준다.
start() {
    local tag="$1"
    write_env "$tag" && start_mysql_without_recreate \
        && compose up -d --no-deps --remove-orphans app && wait_app_healthy \
        && publish_web "$tag" \
        && compose up -d --no-deps caddy && reload_caddy && wait_caddy
}

# 배포는 app·caddy·mysql·alloy만 올리므로 compose 파일에 새로 생긴 서비스는 만들어지지 않는다.
warn_services_not_running() {
    local running service
    running="$(compose ps --services 2> /dev/null)" || return 0
    for service in $(compose config --services 2> /dev/null); do
        if ! grep -qx "$service" <<< "$running"; then
            warn "compose 파일의 $service 서비스가 실행 중이 아니다. 이 정의는 이번 배포에서 검증되지 않았다. 서버에서 '$SERVER_COMPOSE_COMMAND up -d $service'로 띄운다"
        fi
    done
}

apply_alloy_definition() {
    if ! compose up -d --no-deps alloy > /dev/null 2>&1; then
        warn "Alloy 정의를 적용하지 못했다. 서비스는 정상이고 지표·로그 수집만 빠질 수 있다"
    fi
}

# Alloy의 MySQL 수집기가 쓰는 계정을 만들고 비밀번호를 Parameter Store 값에 맞춘다. 배포할 때마다 실행해도 결과가 같다.
# 권한은 서버 상태(PROCESS, REPLICATION CLIENT)와 performance_schema 읽기다. 앱 테이블(회원, 장소 등)은 읽지 못한다.
# 다만 PROCESS 권한이 있으면 SHOW PROCESSLIST로 실행 중인 SQL 문장을 읽을 수 있고, 그 문장에는 값(로그인 이메일 등)이 들어 있을 수 있다.
# 수집기의 기본 수집 항목(info_schema.innodb_cmp 등)이 PROCESS 권한을 요구하므로 이 권한은 뺄 수 없다.
# 그래서 이 계정의 비밀번호는 Alloy 컨테이너와 서버 안에서만 쓰고, MySQL 포트는 밖에 열지 않는다.
# root 비밀번호는 명령 인자가 아니라 MYSQL_PWD 환경 변수로, SQL은 표준 입력으로 넘겨서 프로세스 목록에 남지 않게 한다.
# 실패해도 서비스는 정상이고 MySQL 지표만 빠지므로, 배포를 실패로 만들지 않고 로그만 남긴다.
# mysql의 오류 메시지는 비밀번호가 든 SQL 문장을 되풀이할 수 있고 이 로그는 공개된다. 그래서 표준 오류는 변수로 받고 오류 번호만 남긴다.
ensure_exporter_user() {
    local errors codes
    if errors="$(MYSQL_PWD="${secrets[DB_ROOT_PASSWORD]}" compose exec -T -e MYSQL_PWD mysql mysql -uroot --batch 2>&1 > /dev/null <<SQL
CREATE USER IF NOT EXISTS '$EXPORTER_USER'@'%' IDENTIFIED BY '${secrets[MYSQL_EXPORTER_PASSWORD]}' WITH MAX_USER_CONNECTIONS 3;
ALTER USER '$EXPORTER_USER'@'%' IDENTIFIED BY '${secrets[MYSQL_EXPORTER_PASSWORD]}' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT ON *.* TO '$EXPORTER_USER'@'%';
GRANT SELECT ON performance_schema.* TO '$EXPORTER_USER'@'%';
SQL
)"; then
        log "MySQL 수집용 계정 확인 완료"
    else
        codes="$(grep -o '^ERROR [0-9]* ([0-9A-Z]*)' <<< "$errors" | head -n 1 || true)"
        log "MySQL 수집용 계정을 맞추지 못했다(${codes:-오류 번호 없음}). 서비스는 정상이고 MySQL 지표만 빠진다"
    fi
}

alloy_config_sum() {
    compose exec -T alloy cat "$ALLOY_CONTAINER_CONFIG" 2> /dev/null | sha256sum | cut -d' ' -f1
}

# 배포 워크플로는 새 config.alloy를 mv로 바꿔 넣는다. 그런데 compose는 이 파일 하나만 Alloy 컨테이너에 바인드 마운트하므로,
# 실행 중인 컨테이너는 바꾸기 전 파일을 계속 본다. 또 compose up은 서비스 정의가 바뀌지 않으면 컨테이너를 다시 만들지 않는다.
# 그래서 컨테이너 안 설정이 서버의 파일과 다르면 Alloy를 다시 시작한다. 다시 시작하면 Docker가 파일을 새로 마운트한다.
# 설정이 같으면 다시 시작하지 않는다. 다시 시작하는 동안은 지표와 로그 수집이 잠깐 끊기기 때문이다.
# 실패해도 서비스는 정상이고 지표만 빠지므로, 배포를 실패로 만들지 않고 로그만 남긴다.
sync_alloy_config() {
    local expected actual
    expected="$(sha256sum < "$ALLOY_CONFIG_FILE" | cut -d' ' -f1)"
    actual="$(alloy_config_sum || true)"
    if [[ "$actual" == "$expected" ]]; then
        log "Alloy 설정 확인 완료"
        return
    fi
    if compose restart alloy > /dev/null 2>&1 && [[ "$(alloy_config_sum || true)" == "$expected" ]]; then
        log "Alloy 설정이 바뀌어 Alloy를 다시 시작했다"
    else
        log "Alloy에 새 설정을 적용하지 못했다. 서비스는 정상이고 지표·로그 수집만 옛 설정으로 돈다"
    fi
}

# 매일 DB를 백업하는 systemd 타이머를 설치하거나 갱신한다. 파일은 배포 워크플로가 이 커밋의 deploy/backup/에서 받아 둔다.
# 백업 결과 지표가 아직 없으면(처음 설치) 백업을 바로 한 번 실행해서, 다음 날 새벽까지 '백업 없음' 알림이 울리지 않게 한다.
# 실패해도 서비스는 정상이므로 배포를 실패로 만들지 않고 로그만 남긴다. 백업이 멈추면 Grafana 알림이 따로 알린다.
install_backup_timer() {
    if [[ -z "${server_env[BACKUP_BUCKET]:-}" ]]; then
        log "server.env에 BACKUP_BUCKET이 없어 백업 타이머를 설치하지 않는다"
        return
    fi
    if ! install -m 644 "$BACKUP_DIR/moewadeul-backup.service" "$BACKUP_DIR/moewadeul-backup.timer" "$SYSTEMD_DIR/" \
        || ! systemctl daemon-reload \
        || ! systemctl enable --now moewadeul-backup.timer > /dev/null 2>&1; then
        log "백업 타이머를 설치하지 못했다"
        return
    fi
    log "백업 타이머 확인 완료: $(systemctl show moewadeul-backup.timer -p NextElapseUSecRealtime --value)"
    if [[ ! -f "$BACKUP_METRICS_FILE" ]]; then
        if systemctl start --no-block moewadeul-backup.service; then
            log "첫 백업을 시작했다. 결과는 journalctl -u moewadeul-backup 으로 본다"
        else
            log "첫 백업을 시작하지 못했다. 서비스는 정상이고, 백업이 멈추면 Grafana 알림이 알린다"
        fi
    fi
}

# 폴더가 없으면 Docker가 root 소유로 만들어 MySQL이 기록을 끈다.
# MySQL은 파일을 연 채 계속 쓰므로, logrotate는 파일을 옮기지 않고 복사한 뒤 비운다(copytruncate).
prepare_mysql_slow_log() {
    if ! install -d -o "$MYSQL_UID" -g "$MYSQL_UID" -m 700 "$MYSQL_SLOW_LOG_DIR"; then
        log "MySQL 느린 쿼리 기록 폴더를 만들지 못했다. 서비스는 정상이고 기록만 빠진다"
        return
    fi
    if ! printf '%s\n' \
        "$MYSQL_SLOW_LOG_DIR/slow.log {" \
        "    size 20M" \
        "    rotate 2" \
        "    copytruncate" \
        "    compress" \
        "    missingok" \
        "    notifempty" \
        "}" > "$LOGROTATE_FILE"; then
        log "느린 쿼리 기록의 logrotate 설정을 쓰지 못했다. 기록 파일이 계속 커질 수 있다"
    fi
}

main() {
    local tag="${1:-}"
    if [[ ! "$tag" =~ ^[A-Za-z0-9._-]{1,128}$ ]]; then
        log "사용법: deploy.sh <이미지 태그>"
        exit 1
    fi

    if ! flock -n 9 2>/dev/null; then
        log "배포 워크플로가 잡은 잠금 없이 실행했다. 서버 파일을 바꾸지 않고 끝낸다"
        exit 1
    fi
    trap '(($? == 0)) || restore_last_successful_files' EXIT

    check_architecture
    load_server_env
    load_secrets

    local previous=""
    [[ -f "$LAST_TAG_FILE" ]] && previous="$(<"$LAST_TAG_FILE")"
    log "배포 시작: $tag (직전에 성공한 태그: ${previous:-없음})"

    write_env "$tag"
    # MySQL 이미지(mysql:8.4)는 배포할 때마다 받지 않는다. 받으면 업스트림에 8.4.x 패치가 나온 날
    # 앱 배포가 DB 컨테이너를 새 버전으로 다시 만들고, 배포를 되돌려도 MySQL 버전은 돌아가지 않는다. MySQL을 올릴 때는 서버에서 직접 받는다.
    if ! pull_images "$tag" || ! check_image_architecture "$tag"; then
        log "이미지를 받지 못했거나 아키텍처가 맞지 않는다. 실행 중인 컨테이너는 그대로 두고 배포 파일을 직전에 성공한 것으로 되돌린다"
        [[ -n "$previous" ]] && write_env "$previous"
        exit 1
    fi

    prepare_mysql_slow_log
    if start "$tag"; then
        save_last_successful_files
        echo "$tag" > "$LAST_TAG_FILE"
        ensure_exporter_user
        apply_alloy_definition
        warn_services_not_running
        sync_alloy_config
        install_backup_timer
        # 사흘 넘게 쓰지 않은 이미지를 지워 디스크를 확보한다. 되돌릴 이미지가 지워져도 GHCR에서 다시 받는다.
        # 지우지 못해도 이미 성공한 배포를 실패로 보고하지 않는다. set -e가 여기서 스크립트를 끝내지 않게 || 로 받는다.
        docker image prune -af --filter "until=72h" > /dev/null || log "오래된 이미지를 지우지 못했다. 배포는 성공했다"
        log "배포 성공: $tag"
        exit 0
    fi

    compose ps
    if [[ -z "$previous" || "$previous" == "$tag" ]]; then
        log "배포 실패. 되돌릴 태그가 없다"
        exit 1
    fi

    log "배포 실패. 직전에 성공한 태그 $previous 로 되돌린다"
    restore_last_successful_files
    trap - EXIT
    if start "$previous"; then
        log "되돌리기 성공: $previous"
        exit 2
    fi
    compose ps
    log "되돌리기도 실패했다. 서버에서 docker compose logs로 원인을 확인한다"
    exit 1
}

main "$@"
