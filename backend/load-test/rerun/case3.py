#!/usr/bin/env python3
"""사례 3 섞인 부하: 한 행사에 신청 300 VU(4회차)와 결제·취소 100 VU(5번씩)를 함께 보내 데드락, 잠금 대기, 503과 정원을 센다.

사용: withlock.sh python3 case3.py <APP_TAG> <실행 이름> [비활성 신청 수=0]
비활성 신청 0건이면 자리 세기가 고유 인덱스 계획을, 5,000건이면 status 인덱스 계획을 탄다(EXPLAIN으로 기록).
신청 회원은 user000001~001200, 결제 대기 회원은 user001201~001700이다(시드 볼륨의 회원 2,000명 안에서 겹치지 않는다).
"""
import json, random, subprocess as sp, sys, time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

import lt

APPLY_VUS, ROUNDS, PAY_VUS, PAY_ACTIONS, CAPACITY, PENDING = 300, 4, 100, 5, 1000, 500
SEAT_HOLDER = "(a.status IN ('PENDING_PAYMENT','CONFIRMED') AND (a.status = 'CONFIRMED' OR a.payment_due_at > UTC_TIMESTAMP(6)))"
user = lambda i: "user%06d@loadtest.invalid" % i


def create_program(admin, csrf, open_delay_s=20):
    _, h, _, _ = lt.request("GET", "/api/programs")
    server_ms = int(datetime.strptime(h["Date"], "%a, %d %b %Y %H:%M:%S GMT").replace(tzinfo=timezone.utc).timestamp() * 1000)
    local_ms = int(time.time() * 1000)
    iso = lambda ms: datetime.fromtimestamp(ms / 1000, timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"
    open_ms = server_ms + open_delay_s * 1000; day = 86400000
    body = {"title": f"섞인 부하 {iso(local_ms)}", "description": "측정", "locationText": "측정", "startAt": iso(server_ms + 2 * day), "endAt": iso(server_ms + 3 * day),
            "capacity": CAPACITY, "fee": 30000, "applyOpenAt": iso(open_ms), "applyCloseAt": iso(open_ms + 3600000), "paymentDeadlineMinutes": 30, "overnight": False}
    st, _, raw, _ = lt.request("POST", "/api/admin/programs", body, csrf=csrf, session=admin, headers={"Idempotency-Key": f"case3-{local_ms}"})
    if st != 201:
        raise RuntimeError(f"행사 생성 실패 {st} {raw[:300]}")
    return json.loads(raw)["programId"], (open_ms - (server_ms - local_ms)) / 1000


def explain(program_id):
    ins = (f"INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at) "
           f"SELECT p.id, 1, 'PENDING_PAYMENT', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM program p WHERE p.id = {program_id} "
           f"AND (SELECT COUNT(*) FROM program_application a WHERE a.program_id = p.id AND {SEAT_HOLDER}) < p.capacity")
    rows = [r.split("\t") for r in lt.sql("EXPLAIN " + ins).splitlines()]
    return [r[6] for r in rows if r[2] == "a"]


def endpoints(path):
    m = json.loads(path.read_text())["metrics"]
    out = {}
    for ep in ("apply", "pay", "cancel"):
        codes = {k[len(ep) + 3:]: v["count"] for k, v in m.items() if k.startswith(f"{ep}_n_") and v.get("count")}
        a = m.get(f"http_req_duration{{name:{ep}}}") or {}
        if codes:
            out[ep] = {"codes": codes, "n": sum(codes.values()), "busy_503": codes.get("503_RESOURCE_BUSY", 0), "http_500": codes.get("500", 0),
                       "p50": a.get("med"), "p95": a.get("p(95)"), "max": a.get("max")}
    return out


def main():
    tag, name = sys.argv[1], sys.argv[2]
    inactive = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    out = lt.run_dir(name)
    if (out / "result.json").exists():
        print("skip", name); return
    lt.require_lock()
    meta = lt.restart_app(tag, False)
    csrf = lt.csrf_token()
    admin = lt.login(csrf, lt.ADMIN_EMAIL)
    with ThreadPoolExecutor(8) as pool:
        sessions = list(pool.map(lambda i: lt.login(csrf, user(i)), range(1, APPLY_VUS * ROUNDS + PENDING + 1)))
    program_id, open_at = create_program(admin, csrf)
    pend_emails = ",".join(f"'{user(i)}'" for i in range(APPLY_VUS * ROUNDS + 1, APPLY_VUS * ROUNDS + PENDING + 1))
    lt.sql(f"INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at) "
           f"SELECT {program_id}, m.id, 'PENDING_PAYMENT', UTC_TIMESTAMP(6) + INTERVAL 60 MINUTE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM member m WHERE m.email IN ({pend_emails})")
    if inactive:
        lt.sql(f"INSERT INTO program_application (program_id, member_id, status, payment_due_at, canceled_at, cancel_reason, created_at, updated_at) "
               f"SELECT {program_id}, m.id, IF(s.n % 2 = 0, 'EXPIRED', 'CANCELED'), UTC_TIMESTAMP(6) - INTERVAL 1 DAY, UTC_TIMESTAMP(6), IF(s.n % 2 = 0, 'EXPIRED', 'USER'), "
               f"UTC_TIMESTAMP(6) - INTERVAL 2 DAY, UTC_TIMESTAMP(6) FROM (SELECT ROW_NUMBER() OVER () n FROM spot LIMIT {inactive}) s "
               f"JOIN member m ON m.email = CONCAT('user', LPAD(1 + s.n % 2000, 6, '0'), '@loadtest.invalid')")
    lt.sql("ANALYZE TABLE program_application")
    pend = [(int(i), e) for i, e in (l.split("\t") for l in lt.sql(f"SELECT pa.id, m.email FROM program_application pa JOIN member m ON m.id = pa.member_id WHERE pa.program_id = {program_id} AND pa.status = 'PENDING_PAYMENT' ORDER BY pa.id").splitlines())]
    by_email = {user(i): s for i, s in zip(range(1, len(sessions) + 1), sessions)}
    ops = ["pay"] * (PAY_VUS * PAY_ACTIONS // 2) + ["cancel"] * (PAY_VUS * PAY_ACTIONS - PAY_VUS * PAY_ACTIONS // 2)
    random.Random(838).shuffle(ops)
    (out / "plan.json").write_text(json.dumps({"csrf": csrf, "programId": program_id}))
    (out / "plan-apply.json").write_text(json.dumps(sessions[:APPLY_VUS * ROUNDS]))
    (out / "plan-pay.json").write_text(json.dumps([{"s": by_email[e], "id": i, "op": op} for (i, e), op in zip(pend, ops)]))
    plan_index = explain(program_id)
    while time.time() < open_at + 1:
        time.sleep(0.2)
    names = ("Innodb_deadlocks", "Innodb_lock_timeouts", "Innodb_row_lock_waits", "Innodb_row_lock_time", "Innodb_row_lock_time_max")
    before = lt.status(*names); t0 = time.time()
    env = {"MODE": "mix", "APPLY_VUS": APPLY_VUS, "ROUNDS": ROUNDS, "ROUND_SLEEP": 7, "PAY_VUS": PAY_VUS, "PAY_ACTIONS": PAY_ACTIONS, "JITTER_MS": 500}
    with open(out / "k6.log", "w") as f:
        rc = sp.call(lt.k6(out, "/r/k/c98.js", env, extra=["--summary-trend-stats", "avg,med,p(95),p(99),max", "--summary-export=/out/k6-summary.json"]), stdout=f, stderr=sp.STDOUT)
    after = lt.status(*names)
    holders = int(lt.sql(f"SELECT COUNT(*) FROM program_application a WHERE a.program_id = {program_id} AND {SEAT_HOLDER}"))
    log = lt.sh("docker", "logs", "--since", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(t0 - 1)), lt.APP).stdout
    res = {**meta, "name": name, "inactive": inactive, "program_id": program_id, "seat_index": plan_index, "k6_exit": rc, "duration_s": round(time.time() - t0, 1),
           "innodb_delta": {k: after[k] - before[k] for k in names if k != "Innodb_row_lock_time_max"}, "row_lock_time_max_ms": after["Innodb_row_lock_time_max"],
           "app_log_deadlock_lines": log.count("Deadlock found"), "seat_holders": holders, "capacity": CAPACITY, "endpoints": endpoints(out / "k6-summary.json"),
           "load_after": __import__("os").getloadavg()}
    if rc != 0:
        raise SystemExit(f"k6 종료 코드 {rc}")
    (out / "result.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
    print(name, "index", plan_index, "deadlocks", res["innodb_delta"]["Innodb_deadlocks"], "waits", res["innodb_delta"]["Innodb_row_lock_waits"], "holders", holders,
          {e: (v["codes"], round(v["p95"] or 0)) for e, v in res["endpoints"].items()})


if __name__ == "__main__":
    main()
