#!/usr/bin/env bash
. "$(dirname "$0")/env.sh"
exec env APP_IMAGE=moewadeul-lt-app:${APP_TAG:-sweep-80b558c2} docker compose -p moewadeul-lt-sweep --project-directory "$D" --env-file "$D/opt.env" \
  -f "$ROOT/deploy/docker-compose.prod.yml" -f "$S/override.yml" ${LT_EXTRA:+-f $LT_EXTRA} "$@"
