#!/usr/bin/env python3
"""사례 1: 인증 기록 약 100만 건에서 IP 한도 조회와 재발송 API, V69 정리 작업 시간을 잰다.

사용: withlock.sh python3 case1.py <APP_TAG> [묶음 수=2]
MySQL은 디스크 제한(ov-disk.yml)과 버퍼 풀 256MB로 띄운다. 인덱스 idx_email_verification_ip_created를 지운 상태와 둔 상태를
묶음마다 번갈아 잰다. 끝에 인덱스를 되돌리고 시드를 지운다. 결과는 results/2026-11-08-rerun/runs/case1/result.jsonl.
"""
import json, sys, threading, time

import lt

TAG = sys.argv[1]
ROUNDS = int(sys.argv[2]) if len(sys.argv) > 2 else 2
OUT = lt.run_dir("2026-11-08-rerun", "case1")
IDX = "idx_email_verification_ip_created"
ROWS = 1_000_000
RES = open(OUT / "result.jsonl", "a")


def out(**k):
    k["t"] = round(time.time(), 1)
    RES.write(json.dumps(k, ensure_ascii=False) + "\n"); RES.flush()
    print(json.dumps(k, ensure_ascii=False)[:260], flush=True)


SEQ = """DROP TABLE IF EXISTS lt1004_seq;
CREATE TABLE lt1004_seq (n INT PRIMARY KEY);
INSERT INTO lt1004_seq SELECT a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000 + 1
FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
 (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
 (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c,
 (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d,
 (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e,
 (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f;
DROP TABLE IF EXISTS lt1004_bg;
CREATE TABLE lt1004_bg (n INT PRIMARY KEY, member_id BIGINT NOT NULL);
INSERT INTO lt1004_bg SELECT ROW_NUMBER() OVER (ORDER BY id), id FROM member WHERE email LIKE 'user%@loadtest.invalid';
SET @pw = (SELECT password_hash FROM member WHERE email = 'user000001@loadtest.invalid');
INSERT IGNORE INTO member (email, password_hash, password_changed_at, nickname, status, role, created_at, updated_at)
SELECT CONCAT('uv1004-', LPAD(s.n, 3, '0'), '@loadtest.invalid'), @pw, UTC_TIMESTAMP(6), CONCAT('uv1004_', s.n), 'UNVERIFIED', 'USER', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM lt1004_seq s WHERE s.n <= 6;
"""


def seed(order):
    """order=random은 시각이 id와 무관한 10-21 시드 모양, order=id는 운영처럼 id 순으로 시각이 늘어나는 모양이다."""
    lt.sql(SEQ)
    lt.sql("DELETE FROM email_verification")
    age = "(CRC32(CONCAT('lt1004e', s.n)) % 31536000)" if order == "random" else f"(31536000 - s.n * 31536000 DIV {ROWS})"
    t = time.time()
    lt.sql(f"""SET @now = UTC_TIMESTAMP(6);
INSERT INTO email_verification (member_id, code_hash, request_ip, attempt_count, expires_at, verified_at, created_at, updated_at)
SELECT b.member_id, SHA2(CONCAT('lt1004v', s.n), 256),
 CONCAT('10.', FLOOR(CRC32(CONCAT('lt1004ip', s.n)) % 200000 / 65536), '.', FLOOR(CRC32(CONCAT('lt1004ip', s.n)) % 200000 / 256) % 256, '.', CRC32(CONCAT('lt1004ip', s.n)) % 256),
 0, @now - INTERVAL {age} SECOND + INTERVAL 10 MINUTE,
 IF(s.n % 10 = 0, NULL, @now - INTERVAL {age} SECOND + INTERVAL 1 MINUTE),
 @now - INTERVAL {age} SECOND, @now - INTERVAL {age} SECOND
FROM lt1004_seq s JOIN lt1004_bg b ON b.n = 1 + (s.n % (SELECT COUNT(*) FROM lt1004_bg));
ANALYZE TABLE email_verification;""", timeout=3600)
    size = lt.sql("SELECT ROUND(data_length/1048576), ROUND(index_length/1048576) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='email_verification'")
    out(kind="seed", order=order, rows=rows(), sec=round(time.time() - t, 1), data_index_mib=size)


def rows():
    return int(lt.sql("SELECT COUNT(*) FROM email_verification"))


def has_idx():
    return lt.sql(f"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='email_verification' AND index_name='{IDX}'") != "0"


def idx(on):
    if on and not has_idx():
        t = time.time(); lt.sql(f"CREATE INDEX {IDX} ON email_verification (request_ip, created_at)"); out(kind="create-index", sec=round(time.time() - t, 2))
    if not on and has_idx():
        lt.sql(f"DROP INDEX {IDX} ON email_verification")
    lt.sql("ANALYZE TABLE email_verification")


def lru():
    """버퍼 풀 안의 이 표 페이지 수와 그중 오래된 쪽(old sublist)에 있는 수. INNODB_BUFFER_PAGE_LRU 전체를 훑어 수 초 걸린다."""
    r = lt.sql("SELECT COUNT(*), COALESCE(SUM(IS_OLD='YES'),0) FROM information_schema.INNODB_BUFFER_PAGE_LRU WHERE TABLE_NAME='`moewadeul`.`email_verification`' AND INDEX_NAME='PRIMARY'", timeout=600).split("\t")
    return {"pages": int(r[0]), "old": int(r[1])}


def scan_sql(ip):
    q = f"SELECT COUNT(id), MIN(created_at) FROM email_verification WHERE request_ip='{ip}' AND created_at > UTC_TIMESTAMP(6) - INTERVAL 1 HOUR"
    names = ("Innodb_data_reads", "Handler_read_rnd_next", "Handler_read_next", "Handler_read_key")
    script = f"FLUSH STATUS; SET @t=NOW(6); {q}; SELECT TIMESTAMPDIFF(MICROSECOND,@t,NOW(6)); " + "".join(
        f"SELECT VARIABLE_VALUE FROM performance_schema.session_status WHERE VARIABLE_NAME='{n}';" for n in names[1:])
    before = lt.status("Innodb_data_reads")["Innodb_data_reads"]
    lines = lt.sql(script).splitlines()
    after = lt.status("Innodb_data_reads")["Innodb_data_reads"]
    us, handler = int(lines[1]), [int(x) for x in lines[2:5]]
    return {"ms": us / 1000, "handler_sum": sum(handler), "rnd_next": handler[0], "data_reads": after - before}


def explain(ip):
    return lt.sql(f"EXPLAIN ANALYZE SELECT COUNT(id), MIN(created_at) FROM email_verification WHERE request_ip='{ip}' AND created_at > UTC_TIMESTAMP(6) - INTERVAL 1 HOUR").replace("\\n", " | ")[:500]


def wipe_limits():
    lt.sql("DELETE v FROM email_verification v JOIN member m ON m.id = v.member_id WHERE m.email LIKE 'uv1004-%';"
           "DELETE FROM outbox_event WHERE event_type='EMAIL_VERIFICATION_REQUESTED'; DELETE FROM email_verification_ip_lock; DELETE FROM signup_duplicate_attempt;")


def resend(label, sessions, csrf):
    wipe_limits()
    t0 = time.time()
    r = [None] * len(sessions)
    def go(i):
        s, _, raw, d = lt.request("POST", "/api/me/email-verification/resend", session=sessions[i], csrf=csrf); r[i] = (s, round(d, 3), lt.error_code(raw))
    ts = [threading.Thread(target=go, args=(i,)) for i in range(len(sessions))]
    [t.start() for t in ts]; [t.join() for t in ts]
    since = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(t0 - 1))
    log = lt.sh("docker", "logs", "--since", since, lt.APP).stdout
    busy = [l[:300] for l in log.splitlines() if "RESOURCE_BUSY" in l or "Lock wait timeout" in l or "LockAcquisition" in l or "PessimisticLock" in l]
    ds = sorted(x[1] for x in r)
    out(kind=label, idx=has_idx(), status=sorted((x[0], x[2]) for x in r), max=ds[-1], med=ds[len(ds) // 2], busy_log_lines=len(busy), busy_log=busy[:3])


def cleanup(order):
    """EmailVerificationCleanupService.deleteExpired와 같은 문장(1,000건씩)을 한 세션에서 더 지울 것이 없을 때까지 부른다."""
    seed(order)
    plan = lt.sql("EXPLAIN FORMAT=TREE DELETE FROM email_verification WHERE created_at < UTC_TIMESTAMP(6) - INTERVAL 7 DAY LIMIT 1000").replace("\\n", " | ")
    before = rows(); r0 = lt.status("Innodb_data_reads", "Innodb_rows_read")
    lt.sql("""DROP PROCEDURE IF EXISTS lt1004_cleanup;
DELIMITER //
CREATE PROCEDURE lt1004_cleanup(OUT batches INT)
BEGIN
  DECLARE n INT DEFAULT 1; DECLARE c DATETIME(6) DEFAULT UTC_TIMESTAMP(6) - INTERVAL 7 DAY; SET batches = 0;
  WHILE n > 0 DO DELETE FROM email_verification WHERE created_at < c LIMIT 1000; SET n = ROW_COUNT(); SET batches = batches + 1; END WHILE;
END//
DELIMITER ;""")
    t = time.time(); b = lt.sql("CALL lt1004_cleanup(@b); SELECT @b;", timeout=3600); sec = time.time() - t
    r1 = lt.status("Innodb_data_reads", "Innodb_rows_read")
    out(kind="cleanup", order=order, idx_created_at=lt.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='email_verification' AND index_name='idx_email_verification_created_at'") != "0",
        deleted=before - rows(), left=rows(), sec=round(sec, 2), batches=int(b.splitlines()[-1]), data_reads=r1["Innodb_data_reads"] - r0["Innodb_data_reads"],
        rows_read=r1["Innodb_rows_read"] - r0["Innodb_rows_read"], plan=plan[:300])
    return sec


def main():
    lt.require_lock()
    meta = lt.restart_app(TAG, True)
    meta["buffer_pool"] = lt.sql("SELECT @@innodb_buffer_pool_size, @@innodb_old_blocks_pct, @@innodb_old_blocks_time, @@version")
    out(kind="meta", **meta)
    if rows() < 900_000:
        seed("random")
    csrf = lt.csrf_token()
    sess = [lt.login(csrf, "uv1004-%03d@loadtest.invalid" % n) for n in range(1, 7)]
    ip_hit = lt.sql("SELECT request_ip FROM email_verification ORDER BY id LIMIT 1")
    for rnd in range(1, ROUNDS + 1):
        for on in (False, True):
            idx(on)
            out(kind="explain", round=rnd, idx=on, plan=explain("10.250.250.250"))
            for i in range(4):
                l0 = lru(); s = scan_sql("10.250.250.250"); l1 = lru()
                out(kind="sql-no-recent-ip", round=rnd, idx=on, i=i, lru_before=l0, lru_after=l1, **s)
            out(kind="sql-seeded-ip", round=rnd, idx=on, **scan_sql(ip_hit))
            for i in range(4):
                resend("api-single", [sess[0]], csrf)
            for i in range(3):
                resend("api-same-member-5", [sess[0]] * 5, csrf)
            for i in range(2):
                resend("api-same-ip-5-members", sess[1:6], csrf)
    idx(True)
    for order in ("random", "id", "random", "id"):
        cleanup(order)
    idx(False)
    for i in range(3):
        resend("cleanup-only-after-delete", [sess[0]], csrf)
    out(kind="size-after-delete", rows=rows(), data_mib=lt.sql("SELECT ROUND(data_length/1048576,1) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='email_verification'"))
    lt.sql("OPTIMIZE TABLE email_verification")
    out(kind="size-after-optimize", rows=rows(), data_mib=lt.sql("SELECT ROUND(data_length/1048576,1) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='email_verification'"))
    for i in range(3):
        resend("cleanup-only-after-optimize", [sess[0]], csrf)
    idx(True)
    lt.sql("DELETE FROM email_verification; DELETE FROM outbox_event WHERE event_type='EMAIL_VERIFICATION_REQUESTED'; DELETE FROM email_verification_ip_lock;"
           "DELETE FROM login_history WHERE attempted_email LIKE 'uv1004-%'; DELETE FROM member WHERE email LIKE 'uv1004-%'; DROP TABLE IF EXISTS lt1004_seq, lt1004_bg; DROP PROCEDURE IF EXISTS lt1004_cleanup;")
    out(kind="end", has_index=has_idx(), rows=rows(), load_after=__import__("os").getloadavg())


if __name__ == "__main__":
    main()
