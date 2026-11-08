#!/usr/bin/env python3
"""공원 경계를 다시 적재해 박지 재판정을 돌리는 동안 박지 쓰기(수정)가 어떻게 되는지 잰다.
잠금을 누가 쥐는지 performance_schema.data_lock_waits로 함께 본다."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import json, sys, time, subprocess, threading

import sweep
S = json.load(open(sweep.OUT + "/sessions.json")); T = json.load(open(sweep.OUT + "/csrf.json"))["token"]
def h(email):
    return {"Cookie": f"SESSION={S[email]}; XSRF-TOKEN={T}", "X-XSRF-TOKEN": T, "Content-Type": "application/json"}
admin = "admin@loadtest.invalid"
rows = sweep.sql("SELECT s.id FROM spot s JOIN bakji_detail d ON d.spot_id=s.id WHERE s.status='ACTIVE' AND s.id % 37 = 29 ORDER BY s.id LIMIT 400")
ids = [r["id"] for r in rows]
user = "user000001@loadtest.invalid"
def patch(i):
    t = time.time(); s, _, b, _ = sweep.http("PATCH", f"/api/bakjis/{ids[i]}", json.dumps({"description": f"재판정 중 수정 {i}"}), h(user)); return s, (time.time() - t) * 1000, b[:80]
base = [patch(i) for i in range(10)]
print("재판정 전 수정:", [round(x[1]) for x in base], [x[0] for x in base])
s, _, b, _ = sweep.http("POST", "/api/admin/sync-jobs", json.dumps({"jobType": "PARK_BOUNDARY"}), h(admin))
print("적재 시작", s, b[:120])
t0 = time.time(); out = []; waits = []
i = 10
while time.time() - t0 < float(sys.argv[1] if len(sys.argv) > 1 else 120):
    st, ms, body = patch(i); out.append((round(time.time() - t0, 1), st, round(ms)))
    w = sweep.sql("SELECT COUNT(*) n FROM performance_schema.data_lock_waits")
    waits.append(int(w[0]["n"]))
    i += 1; time.sleep(0.5)
runs = sweep.sql("SELECT id, job_type, status, processed_count, started_at, finished_at, LEFT(error_message, 120) err FROM sync_job_run WHERE id > (SELECT MAX(id) - 3 FROM sync_job_run) ORDER BY id")
print("재판정 중 수정(초, 코드, ms):", out)
print("잠금 대기 수:", waits)
print("실행 기록:", runs)
bad = [x for x in out if x[1] >= 300]
print(f"수정 {len(out)}건, 실패 {len(bad)}건, 1초 넘은 성공 {sum(1 for x in out if x[1] < 300 and x[2] > 1000)}건, 최대 {max(x[2] for x in out)} ms")
