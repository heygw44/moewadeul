#!/usr/bin/env python3
"""장부의 API: 행으로 요청 묶음을 만들어 k6로 재고, 쿼리 다이제스트를 모은다.

  sweep.py plan                         시드에서 경로 변수와 행위자를 뽑고 로그인해 out/plans/를 만든다
  sweep.py smoke [글자]                 API마다 한 건씩 보내 2xx인지 보고 응답을 찍는다. 추적 ID를 남긴다
  sweep.py seq                          API마다 혼자 재기
  sweep.py load                         API마다 50 VU로 재기
  sweep.py digest <이름>                performance_schema 다이제스트를 out/digest-<이름>.tsv로 저장
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import concurrent.futures as cf
import csv
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import uuid


import apis  # noqa: E402

OUT = D + "/out"
PLANS = OUT + "/plans"
BASE = "http://127.0.0.1:47180"
APP = "http://127.0.0.1:47181"
NET = "moewadeul-lt-sweep_default"
SEQ_READ = 60
SEQ_WRITE = 30
PARTS = 83
SESSIONS = OUT + "/sessions.json"


def sql(q):
    p = subprocess.run([S + "/sql.sh", "--batch"], input=("SET SESSION sql_mode=REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY','');\n" + q).encode(), capture_output=True)
    if p.returncode:
        raise RuntimeError(p.stderr.decode()[:400] + "\n" + q[:300])
    lines = p.stdout.decode().rstrip("\n").split("\n")
    if not lines or not lines[0]:
        return []
    head = lines[0].split("\t")
    return [dict(zip(head, ln.split("\t"))) for ln in lines[1:]]


def ledger_apis():
    with open(LEDGER) as f:
        return [r["item"] for r in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE) if r["id"].startswith("API:")]


def slug(key):
    return re.sub(r"[^a-zA-Z0-9]+", "-", key).strip("-")


def http(method, path, body=None, headers=None, base=None):
    req = urllib.request.Request((APP if base else BASE) + path, method=method, data=None if body is None else body.encode(), headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, dict(r.headers), r.read().decode(errors="replace"), r.headers.get_all("Set-Cookie") or []
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode(errors="replace"), e.headers.get_all("Set-Cookie") or []


def csrf():
    _, _, _, cookies = http("GET", "/api/programs")
    for c in cookies:
        m = re.match(r"XSRF-TOKEN=([^;]+)", c)
        if m:
            return m.group(1)
    raise RuntimeError("CSRF 토큰 없음")


def login_all(emails, token):
    known = json.load(open(SESSIONS)) if os.path.exists(SESSIONS) else {}
    todo = sorted(e for e in emails if e not in known)

    def one(email):
        s, _, b, cookies = http("POST", "/api/auth/login", json.dumps({"email": email, "password": apis.PASSWORD}),
                                {"Content-Type": "application/json", "Cookie": f"XSRF-TOKEN={token}", "X-XSRF-TOKEN": token})
        for c in cookies:
            m = re.match(r"SESSION=([^;]+)", c)
            if m and s == 200:
                return email, m.group(1)
        print("로그인 실패", email, s, b[:160])
        return email, None

    with cf.ThreadPoolExecutor(4) as ex:
        for i, (e, sid) in enumerate(ex.map(one, todo)):
            if sid:
                known[e] = sid
            if i % 500 == 499:
                print(f"로그인 {i + 1}/{len(todo)}", flush=True)
                json.dump(known, open(SESSIONS, "w"))
    json.dump(known, open(SESSIONS, "w"))
    return known


def rows_for(a, part):
    if a.rows is None:
        return [{}]
    if isinstance(a.rows, list):
        out = [dict(r) for r in a.rows]
        for r in out:
            if r.get("spotId") == "__SPOT__":
                r["spotId"] = sql("SELECT spot_id s FROM community_post WHERE spot_id IS NOT NULL GROUP BY spot_id ORDER BY COUNT(*) DESC LIMIT 1")[0]["s"]
        return out
    q = re.sub(r"\{P:([\w.]+)\}", lambda m: f"MOD({m.group(1)}, {PARTS}) = {part}", a.rows)
    return sql(q)


def build(a, part, token, sessions):
    rows = rows_for(a, part)
    path_vars = re.findall(r"\{(\w+)\}", a.key)
    method, path = a.key.split(" ", 1)
    reqs = []
    for i, r in enumerate(rows):
        u = path
        for v in path_vars:
            if v not in r:
                raise KeyError(f"{a.key}: 행에 {v} 없음 {list(r)}")
            u = u.replace("{" + v + "}", str(r[v]))
        h = {"Cookie": f"XSRF-TOKEN={token}", "X-XSRF-TOKEN": token}
        actor = r.get("actor") or a.actor
        if actor:
            sid = sessions.get(actor)
            if not sid:
                continue
            h["Cookie"] = f"SESSION={sid}; XSRF-TOKEN={token}"
        q = (a.query(r) if a.query else None)
        if q:
            q = {k: v for k, v in q.items() if k != "actor"}
            if q:
                u += "?" + "&".join(f"{k}={urllib.parse.quote(str(v))}" for k, v in q.items())
        req = {"m": method, "u": u, "h": h}
        if a.body:
            req["b"] = json.dumps(a.body(r, i), ensure_ascii=False)
            h["Content-Type"] = "application/json"
        if a.idem:
            h["Idempotency-Key"] = uuid.uuid4().hex
        if a.direct:
            h["X-Forwarded-For"] = f"10.77.{i // 250}.{i % 250 + 1}"
        reqs.append(req)
    return {"api": a.key, "consume": a.consume, "ok": a.ok, "base": "http://app:8080" if a.direct else None, "requests": reqs}


def prepare_program_fixtures(token, sessions):
    """신청, 결제, 취소, 빈자리 알림이 쓸 행사를 만든다. 재실행하면 있는 것을 쓴다."""
    # 알림 메일은 24시간 발송 수로 막힌다. 시드의 발송 시각이 그 창에 있으면 메일 작업이 돌지 않는다.
    sql("UPDATE notification SET email_sent_at = UTC_TIMESTAMP() - INTERVAL 3 DAY WHERE email_sent_at > UTC_TIMESTAMP() - INTERVAL 3 DAY")
    if sql("SELECT COUNT(*) c FROM program WHERE title LIKE 'sweep-open%'")[0]["c"] != "0":
        return
    sql("""
    INSERT INTO program (title, description, location_text, start_at, end_at, capacity, fee, apply_open_at, apply_close_at, payment_deadline_minutes, overnight, status, created_by, created_at, updated_at)
    SELECT CONCAT(k.t, '-', s.n), '측정용 행사', '측정', UTC_TIMESTAMP() + INTERVAL 20 DAY, UTC_TIMESTAMP() + INTERVAL 21 DAY, k.cap, 30000,
           UTC_TIMESTAMP() - INTERVAL 1 HOUR, UTC_TIMESTAMP() + INTERVAL 10 DAY, 1440, 0, 'SCHEDULED', (SELECT id FROM member WHERE email='admin@loadtest.invalid'), UTC_TIMESTAMP(), UTC_TIMESTAMP()
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) s0
    JOIN (SELECT ROW_NUMBER() OVER () - 1 n FROM member LIMIT 500) s ON s.n < CASE s0.n WHEN 0 THEN 1 WHEN 1 THEN 2 WHEN 2 THEN 500 ELSE 230 END
    JOIN (SELECT 0 n, 'sweep-open' t, 100000 cap UNION ALL SELECT 1, 'sweep-full', 1 UNION ALL SELECT 2, 'sweep-edit', 100 UNION ALL SELECT 3, 'sweep-cancel', 300) k ON k.n = s0.n;
    -- 결제와 취소가 쓸 결제 대기 신청 1000건
    INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at)
    SELECT (SELECT id FROM program WHERE title='sweep-open-0'), id, 'PENDING_PAYMENT', UTC_TIMESTAMP() + INTERVAL 2 DAY, UTC_TIMESTAMP(), UTC_TIMESTAMP()
    FROM member WHERE email LIKE 'bg004%' AND status='ACTIVE' LIMIT 5000;
    -- 정원 1 행사는 이미 찼다
    INSERT INTO program_application (program_id, member_id, status, payment_due_at, confirmed_at, created_at, updated_at)
    SELECT p.id, m.id, 'CONFIRMED', UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP()
    FROM program p JOIN member m ON m.email=IF(p.title='sweep-full-0', 'bg0030000@loadtest.invalid', 'bg0030001@loadtest.invalid') WHERE p.title LIKE 'sweep-full-%';
    -- 취소 행사마다 신청 300건
    INSERT IGNORE INTO program_application (program_id, member_id, status, payment_due_at, confirmed_at, created_at, updated_at)
    SELECT p.id, m.id, 'CONFIRMED', UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP()
    FROM program p JOIN (SELECT ROW_NUMBER() OVER () rn, id FROM member WHERE email LIKE 'bg005%' AND status='ACTIVE' LIMIT 10000) m ON m.rn % 33 = MOD(p.id, 33)
    WHERE p.title LIKE 'sweep-cancel%';
    -- 결제 기한이 지난 신청. 결제 만료 작업의 재료다.
    INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at)
    SELECT (SELECT id FROM program WHERE title='sweep-open-0'), id, 'PENDING_PAYMENT', UTC_TIMESTAMP() - INTERVAL 1 MINUTE, UTC_TIMESTAMP() - INTERVAL 1 DAY, UTC_TIMESTAMP() - INTERVAL 1 DAY
    FROM member WHERE email LIKE 'bg0060%' AND status='ACTIVE' LIMIT 200;
    -- 사진 정리 작업의 재료는 절반 남기고, 나머지는 글·기록·후기에 붙일 수 있게 24시간 안으로 옮긴다.
    UPDATE community_image SET created_at = UTC_TIMESTAMP() - INTERVAL 1 HOUR WHERE post_id IS NULL AND object_key LIKE '%lt-orphan%' AND id % 2 = 0;
    UPDATE memory_image SET created_at = UTC_TIMESTAMP() - INTERVAL 1 HOUR WHERE memory_id IS NULL AND object_key LIKE '%lt-orphan%' AND id % 2 = 0;
    UPDATE review_image SET created_at = UTC_TIMESTAMP() - INTERVAL 1 HOUR WHERE review_id IS NULL AND object_key LIKE '%lt-orphan%' AND id % 2 = 0;
    -- 확정 신청에는 결제가 있어야 취소와 환불이 돈다.
    INSERT IGNORE INTO payment (program_application_id, amount, status, paid_at, created_at, updated_at)
    SELECT a.id, 30000, 'PAID', UTC_TIMESTAMP(), UTC_TIMESTAMP(), UTC_TIMESTAMP() FROM program_application a JOIN program p ON p.id=a.program_id WHERE p.title LIKE 'sweep-cancel%' OR p.title LIKE 'sweep-full-%';
    -- 자리가 나면 알림을 받을 회원. 신청을 취소하는 경로가 이들을 읽고 고친다.
    INSERT IGNORE INTO program_vacancy_alert (program_id, member_id, created_at)
    SELECT p.id, m.id, UTC_TIMESTAMP() FROM program p JOIN member m ON m.email LIKE 'bg00610%' WHERE p.title='sweep-full-1';
    -- 리더가 직접 마감한 캠프. 다시 여는 API의 재료다.
    UPDATE basecamp SET status='CLOSED', closed_reason='LEADER' WHERE status='RECRUITING' AND MOD(id, 40) = 21 LIMIT 300;
    -- 이메일 인증은 마쳤지만 본인확인을 하지 않은 회원
    INSERT INTO member (email, password_hash, password_changed_at, nickname, status, role, email_verified_at, created_at, updated_at)
    SELECT CONCAT('noid-', LPAD(s.n, 5, '0'), '@loadtest.invalid'), (SELECT password_hash FROM (SELECT password_hash FROM member WHERE email='bg0000001@loadtest.invalid') x), UTC_TIMESTAMP() - INTERVAL 30 DAY, CONCAT('ni', s.n), 'ACTIVE', 'USER', UTC_TIMESTAMP() - INTERVAL 30 DAY, UTC_TIMESTAMP() - INTERVAL 30 DAY, UTC_TIMESTAMP() - INTERVAL 30 DAY
    FROM (SELECT ROW_NUMBER() OVER () n FROM member LIMIT 300) s;
    """)


def cmd_plan(args):
    apis.set_run(int(os.environ.get("RUN", "1")))
    os.makedirs(PLANS, exist_ok=True)
    wanted = ledger_apis()
    missing = [k for k in wanted if k not in apis.REG]
    extra = [k for k in apis.REG if k not in wanted]
    if missing or extra:
        print("사양 없음:", missing, "\n장부에 없음:", extra)
        return 1
    token = csrf()
    prepare_program_fixtures(token, {})
    only = args[0] if args else None
    keys = [k for k in wanted if not only or only in k]
    raw = {}
    emails = {apis.ADMIN}
    for part, key in enumerate(keys):
        a = apis.REG[key]
        try:
            rows = rows_for(a, wanted.index(key) % PARTS)
        except RuntimeError as e:
            print("SQL 오류", key, str(e)[:300])
            rows = []
        raw[key] = rows
        for r in rows:
            if r.get("actor"):
                emails.add(r["actor"])
        if a.actor:
            emails.add(a.actor)
        print(f"{len(rows):5d} {key}", flush=True)
    sessions = login_all(emails, token)
    json.dump({"token": token}, open(OUT + "/csrf.json", "w"))
    bad = 0
    for key in keys:
        a = apis.REG[key]
        try:
            plan = build(a, wanted.index(key) % PARTS, token, sessions)
        except (RuntimeError, KeyError) as e:
            print("계획 오류", key, str(e)[:200])
            plan = {"api": key, "consume": a.consume, "ok": a.ok, "requests": []}
        if not plan["requests"]:
            print("요청 없음:", key)
            bad += 1
        json.dump(plan, open(f"{PLANS}/{slug(key)}.json", "w"), ensure_ascii=False)
    print(f"계획 {len(keys)}개, 빈 계획 {bad}, 세션 {len(sessions)}")
    return 0


def load_plans(only=None):
    plans = []
    # 공원 경계 적재는 끝난 뒤 박지 재판정을 바로 이어서 돌려 장소 잠금을 오래 쥔다. 다른 API를 재는 동안 돌지 않게 맨 뒤로 보낸다.
    last = "POST /api/admin/sync-jobs"
    for key in sorted(ledger_apis(), key=lambda k: k == last):
        if only and only not in key:
            continue
        f = f"{PLANS}/{slug(key)}.json"
        if os.path.exists(f):
            plans.append((key, json.load(open(f))))
    return plans


def cmd_smoke(args):
    """요청 하나(소비형은 맨 뒤 행)를 보내 코드와 추적 ID를 남긴다. 다시 재기와 행이 겹치지 않게 뒤에서 뽑는다."""
    only = args[0] if args else None
    out = []
    plans = load_plans(only)
    for key, plan in plans:
        if not plan["requests"]:
            out.append(dict(api=key, status="NOPLAN"))
            print("NOPLAN", key)
            continue
        if plan["consume"]:
            # 적재 작업처럼 행마다 하는 일이 다른 API는 행을 모두 한 번씩 부른다.
            batch = plan["requests"][:] if len(plan["requests"]) < 10 else [plan["requests"].pop()]
            if len(plan["requests"]) < 10:
                plan["requests"] = []
        else:
            batch = plan["requests"][:12]
        for r in batch:
            s, h, b, _ = http(r["m"], r["u"], r.get("b"), r["h"], base=plan.get("base"))
            out.append(dict(api=key, status=s, trace=h.get("X-Trace-Id", ""), body=b[:300], u=r["u"]))
            good = plan["ok"] and s in plan["ok"] or (not plan["ok"] and 200 <= s < 300)
            print(s, key, r["u"] if not good else "", "" if good else b[:260], flush=True)
        if plan["consume"]:
            json.dump(plan, open(f"{PLANS}/{slug(key)}.json", "w"), ensure_ascii=False)
    name = os.environ.get("SMOKE_OUT", "smoke")
    path = f"{OUT}/{name}.json"
    previous = json.load(open(path)) if os.path.exists(path) else []
    json.dump(previous + out, open(path, "w"), ensure_ascii=False)
    apis_all = {x["api"] for x in out}
    good = {x["api"] for x in out if isinstance(x["status"], int) and 200 <= x["status"] < 300}
    print(f"2xx 받은 API {len(good)}/{len(apis_all)}, 못 받은 API {sorted(apis_all - good)}")
    return 0


def k6(plan_path, mode, out, count=None, skip=0, duration=None, vus=50):
    e = ["-e", f"PLAN=/d/{os.path.relpath(plan_path, D)}", "-e", f"MODE={mode}", "-e", "BASE_URL=http://caddy:80", "-e", f"OUT=/d/{os.path.relpath(out, D)}", "-e", f"VUS={vus}", "-e", f"SKIP={skip}"]
    if count:
        e += ["-e", f"COUNT={count}"]
    if duration:
        e += ["-e", f"DURATION={duration}"]
    p = subprocess.run(["docker", "run", "--rm", "-u", "0", "--network", NET, "--cpuset-cpus", "4-7", "--ulimit", "nofile=65536:65536", "-v", f"{D}:/d", "-v", f"{S}:/s:ro", "grafana/k6", "run", "-q", "--no-usage-report"] + e + ["/s/replay.js"], capture_output=True)
    text = p.stdout.decode(errors="replace") + p.stderr.decode(errors="replace")
    return [ln for ln in text.split("\n") if "BAD " in ln][:2], p.returncode


def fmt(r):
    if not r:
        return "없음"
    a = r.get("ok") or {}
    return f"n={r['count']} p50={a.get('med', 0):.1f} p95={a.get('p(95)', 0):.1f} max={a.get('max', 0):.1f} rps={r['rate']:.1f} codes={r['statuses']}"


def run_phase(phase, args):
    only = args[0] if args else None
    res_dir = f"{OUT}/{phase}"
    os.makedirs(res_dir, exist_ok=True)
    state = json.load(open(OUT + "/cursor.json")) if os.path.exists(OUT + "/cursor.json") else {}
    for key, plan in load_plans(only):
        path = f"{PLANS}/{slug(key)}.json"
        out = f"{res_dir}/{slug(key)}.json"
        if os.path.exists(out) and not os.environ.get("FORCE"):
            continue
        total = len(plan["requests"])
        if total == 0:
            continue
        if phase == "seq":
            count = min(SEQ_WRITE, total) if plan["consume"] else SEQ_READ
            bad, rc = k6(path, "seq", out, count=count)
            state[key] = count if plan["consume"] else 0
        elif phase == "warm":
            count = 20 if not plan["consume"] else 0
            if not count:
                continue
            bad, rc = k6(path, "seq", out, count=count)
        else:
            skip = state.get(key, 0)
            if plan["consume"]:
                if total - skip <= 0:
                    continue
                bad, rc = k6(path, "load", out, count=total - skip, skip=skip)
            else:
                bad, rc = k6(path, "load", out, duration=os.environ.get("LOAD_DURATION", "15s"))
        json.dump(state, open(OUT + "/cursor.json", "w"))
        r = json.load(open(out)) if os.path.exists(out) else None
        print(f"{phase} {key}: {fmt(r)}", *bad, flush=True)
    return 0


DIGEST_Q = """SELECT DIGEST, COUNT_STAR, SUM_NO_INDEX_USED, SUM_NO_GOOD_INDEX_USED, SUM_SORT_MERGE_PASSES, SUM_SORT_ROWS, SUM_CREATED_TMP_TABLES, SUM_CREATED_TMP_DISK_TABLES,
 SUM_ROWS_EXAMINED, SUM_ROWS_SENT, SUM_ROWS_AFFECTED, ROUND(AVG_TIMER_WAIT/1e9,3) avg_ms, ROUND(MAX_TIMER_WAIT/1e9,3) max_ms, ROUND(SUM_LOCK_TIME/1e9,3) lock_ms, REPLACE(REPLACE(DIGEST_TEXT, '\\n', ' '), '\\t', ' ') DIGEST_TEXT
 FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='moewadeul' AND DIGEST IS NOT NULL"""


def cmd_digest(args):
    rows = sql(DIGEST_Q)
    with open(f"{OUT}/digest-{args[0]}.tsv", "w") as o:
        if rows:
            w = csv.DictWriter(o, rows[0].keys(), delimiter="\t", quoting=csv.QUOTE_NONE, escapechar="\\")
            w.writeheader()
            w.writerows(rows)
    print(f"다이제스트 {len(rows)}개 저장")
    return 0


def main():
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == "plan":
        return cmd_plan(args)
    if cmd == "smoke":
        return cmd_smoke(args)
    if cmd in ("seq", "load", "warm"):
        return run_phase(cmd, args)
    if cmd == "digest":
        return cmd_digest(args)
    if cmd == "reset-digest":
        sql("TRUNCATE TABLE performance_schema.events_statements_summary_by_digest")
        return 0
    return 1


import urllib.parse  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
