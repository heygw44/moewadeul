#!/usr/bin/env bash
# 사용: LT_DISK=0|1 APP_TAG=<태그> lt.sh <compose 인자>. 운영 compose 위에 override.yml을 겹치고 LT_DISK=1이면 ov-disk.yml도 겹친다.
# LT_DISK를 빠뜨리면 멈춘다. 디스크 제한이 조용히 빠진 채 재지 않기 위해서다.
. "$(dirname "$0")/env.sh"
: "${LT_DISK:?LT_DISK=0 또는 1을 정한다}" "${APP_TAG:?APP_TAG를 정한다(image.sh가 찍은 태그)}"
FILES=(-f "$ROOT/deploy/docker-compose.prod.yml" -f "$S/override.yml")
case "$LT_DISK" in 1) FILES+=(-f "$S/ov-disk.yml");; 0) ;; *) echo "LT_DISK는 0 또는 1" >&2; exit 2;; esac
for f in ${LT_EXTRA:-}; do FILES+=(-f "$f"); done
exec env LT_TOOLS="$S" APP_IMAGE="moewadeul-lt-app:$APP_TAG" docker compose -p "$LT_PROJECT" --project-directory "$LT_DIR" --env-file "$LT_DIR/opt.env" "${FILES[@]}" "$@"
