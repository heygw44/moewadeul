#!/usr/bin/env bash
# compose의 caddy 이미지 이름을 찍는다. 태그가 Dockerfile의 Caddy 버전과 모듈 커밋으로 만든 이름과 다르면 실패한다.
# Dependabot이 Dockerfile의 Caddy만 올리면 CI가 여기서 멈춰, compose 태그를 함께 바꾸게 한다.
set -euo pipefail
dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
caddy="$(sed -n 's/^FROM caddy:\([0-9.]*\)-alpine AS server$/\1/p' "$dir/Dockerfile")"
module="$(sed -n 's/^RUN xcaddy build --with github.com\/mholt\/caddy-ratelimit@\([0-9a-f]\{7\}\)[0-9a-f]*$/\1/p' "$dir/Dockerfile")"
expected="ghcr.io/heygw44/moewadeul-caddy:server-$caddy-ratelimit-$module"
actual="$(sed -n 's/^    image: \(ghcr\.io\/heygw44\/moewadeul-caddy:.*\)$/\1/p' "$dir/../docker-compose.prod.yml")"
if [[ -z "$caddy" || -z "$module" ]] || ! grep -qx "FROM caddy:$caddy-builder-alpine AS caddy-build" "$dir/Dockerfile" || [[ "$actual" != "$expected" ]]; then
    echo "compose의 caddy 이미지(${actual:-없음})가 Dockerfile로 만든 이름 $expected 와 다르다. 빌드 단계와 서버 단계의 Caddy 버전도 같아야 한다" >&2
    exit 1
fi
echo "$expected"
