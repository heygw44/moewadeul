#!/usr/bin/env bash
# 저장소의 deploy/deploy.sh를 로컬 측정 틀에 맞게 고쳐 opt/deploy.lt.sh로 쓴다. 고친 곳은 opt/deploy.lt.diff에 남는다.
# 바꾸는 것: 경로, compose 프로젝트와 override, 포트(8080→28080, 8081→28082, 80→28081), 이미지 저장소, 가짜 aws,
# 이미지 받기와 지우기, 잠금(flock), Alloy 띄우기, macOS의 mv와 install. 배포 순서와 판단은 그대로다.
set -euo pipefail
source "$(dirname "$0")/env.sh"
src="${DEPLOY_SRC:-$ROOT/deploy/deploy.sh}" out="$OPT/deploy.lt.sh"
sed \
    -e "s#^readonly DEPLOY_DIR=/opt/moewadeul#readonly DEPLOY_DIR=$OPT#" \
    -e 's#"\$arch" != "aarch64"#"$arch" != "arm64"#' \
    -e "s#-f \"\\\$COMPOSE_FILE\" --env-file#-p $P -f \"\$COMPOSE_FILE\" -f \"\$DEPLOY_DIR/override.yml\" --env-file#" \
    -e '/docker image prune -af/d' \
    -e 's#^export PATH="\$PATH:/snap/bin"#export PATH="'"$S"'/bin:$PATH" DEPLOY_LT_SECRETS="'"$OPT"'/.secrets"#' \
    -e 's#^readonly APP_REPO=.*#readonly APP_REPO=moewadeul-lt-app#' \
    -e 's#^readonly CADDY_REPO=.*#readonly CADDY_REPO=moewadeul-lt-caddy#' \
    -e 's#compose pull --quiet app \&\& compose pull --quiet --policy missing caddy \&\& docker pull --quiet "\$CADDY_REPO:\$tag" > /dev/null#true#' \
    -e 's#mv -T "\$WEB_DIR/current.tmp"#mv -fh "$WEB_DIR/current.tmp"#' \
    -e 's#install -d -o "\$MYSQL_UID" -g "\$MYSQL_UID" -m 700#mkdir -p -m 777#' \
    -e 's#^readonly LOGROTATE_FILE=.*#readonly LOGROTATE_FILE="$DEPLOY_DIR/logrotate-mysql-slow"#' \
    -e 's#compose up -d --no-deps alloy#true#' \
    -e 's#http://127.0.0.1:8080/actuator/health#http://127.0.0.1:28080/actuator/health#' \
    -e 's#http://127.0.0.1:8081/actuator/health#http://127.0.0.1:28082/actuator/health#' \
    -e 's#http://127.0.0.1/)"#http://127.0.0.1:28081/)"#' \
    -e 's#if ! flock -n 9 2>/dev/null; then#if false; then#' \
    "$src" > "$out"
chmod +x "$out"
diff "$src" "$out" > "$OPT/deploy.lt.diff" || true
hunks="$(grep -c '^[0-9]' "$OPT/deploy.lt.diff")"
# deploy.sh가 바뀌어 sed 한 줄이 맞지 않으면 그 부분은 운영 값 그대로 남는다. 개수가 다르면 멈춘다.
if [[ "$hunks" != 14 ]]; then
    echo "deploy.sh에서 바꾼 곳이 ${hunks}개다(기대 14개). opt/deploy.lt.diff를 보고 sed를 고친다" >&2
    exit 1
fi
