"""도구와 결과 폴더 위치. 결과는 gitignore된 results 아래에 쌓는다. SWEEP_DIR로 바꿀 수 있다."""
import os

S = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(S, "../../.."))
D = os.environ.get("SWEEP_DIR", os.path.join(ROOT, "backend/load-test/results/sweep"))
LEDGER = os.environ.get("LEDGER", os.path.join(ROOT, "docs/portfolio/tools/ledger.tsv"))
