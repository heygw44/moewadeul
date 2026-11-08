# 재현 도구가 함께 읽는 위치와 이름. lt.py도 같은 규칙을 쓴다.
S="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$S/../../.." && pwd)"
# results/는 gitignore라 워크트리에서 돌려도 원래 체크아웃의 폴더를 쓴다.
MAIN="$(dirname "$(git -C "$S" rev-parse --path-format=absolute --git-common-dir)")"
# 시드 볼륨 moewadeul-lt-weather_mysql-data의 비밀번호가 이 폴더의 opt.env에 묶여 있다.
LT_DIR="${LT_DIR:-$MAIN/backend/load-test/results/2026-11-03-local-openai-s3-wait}"
LT_PROJECT="${LT_PROJECT:-moewadeul-lt-weather}"
LT_LOCK=/tmp/moewadeul-loadtest.lock
export ROOT MAIN LT_DIR LT_PROJECT LT_LOCK
