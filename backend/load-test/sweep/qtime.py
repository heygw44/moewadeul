#!/usr/bin/env python3
"""SQL 한 문장을 여러 번 돌려 서버 쪽 시간(performance_schema TIMER_WAIT)과 읽은 행을 잰다. 쓰기는 트랜잭션 안에서 돌리고 되돌린다.
  qtime.py <횟수> <SQL 파일>   결과: 회차별 ms, 읽은 행, 돌려준 행, 바꾼 행
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import subprocess, sys, statistics
STAT = "SELECT '@@', ROUND(TIMER_WAIT/1e9,3), ROWS_EXAMINED, ROWS_SENT, ROWS_AFFECTED FROM performance_schema.events_statements_history WHERE THREAD_ID=PS_CURRENT_THREAD_ID() AND EVENT_NAME NOT LIKE '%begin%' ORDER BY EVENT_ID DESC LIMIT 1;"


def run(sql, n):
    out = []
    for _ in range(n):
        script = f"START TRANSACTION;\n{sql.strip().rstrip(';')};\n{STAT}\nROLLBACK;\n"
        p = subprocess.run([S + "/sql.sh", "--batch", "--skip-column-names"], input=script.encode(), capture_output=True)
        line = [l for l in p.stdout.decode().split("\n") if l.startswith("@@")]
        if not line:
            raise RuntimeError(p.stderr.decode()[:300])
        _, ms, ex, sent, aff = line[-1].split("\t")
        out.append((float(ms), int(ex), int(sent), int(aff)))
    return out


if __name__ == "__main__":
    r = run(open(sys.argv[2]).read(), int(sys.argv[1]))
    ms = [x[0] for x in r]
    print(f"ms median {statistics.median(ms):.2f} range {min(ms):.2f}~{max(ms):.2f} examined {r[-1][1]} sent {r[-1][2]} affected {r[-1][3]}")
