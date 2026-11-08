#!/usr/bin/env bash
# 사용: image.sh. 지금 HEAD로 bootJar를 만들고 git.sha 라벨을 붙인 측정 이미지 moewadeul-lt-app:rerun-<sha8>을 만든다.
# 커밋하지 않은 변경이 있으면 멈춘다. 라벨과 코드가 어긋난 이미지로 재지 않기 위해서다.
set -euo pipefail
. "$(dirname "$0")/env.sh"
cd "$ROOT"
[ -z "$(git status --porcelain -- backend/src backend/build.gradle.kts backend/Dockerfile backend/scripts)" ] || { echo "커밋하지 않은 backend 변경이 있다" >&2; exit 1; }
SHA=$(git rev-parse HEAD); TAG=moewadeul-lt-app:rerun-${SHA:0:8}
(cd backend && ./gradlew -q bootJar)
JAR=$(ls backend/build/libs/*.jar)
[ "$(echo "$JAR" | wc -l)" -eq 1 ] || { echo "jar가 하나가 아니다: $JAR" >&2; exit 1; }
docker build -q --label "git.sha=$SHA" -t "$TAG" backend >/dev/null
echo "$TAG"
