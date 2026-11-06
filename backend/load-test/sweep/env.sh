# 셸 도구가 함께 읽는 위치. paths.py와 같은 규칙이다.
S="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$S/../../.." && pwd)"
D="${SWEEP_DIR:-$ROOT/backend/load-test/results/sweep}"
export SWEEP_TOOLS="$S" SWEEP_DIR="$D"
