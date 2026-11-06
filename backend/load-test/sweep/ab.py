#!/usr/bin/env python3
"""전후를 번갈아 잰다. 전 = 인덱스 없음 + 옛 SQL, 후 = 인덱스 있음 + 새 SQL. 회차마다 인덱스를 만들고 지운다.

  ab.py <사례 이름> [회차=5]    결과를 ab/<사례>.json과 표로 남긴다

사례는 CASES에 적는다. before/after는 ab/ 아래 SQL 파일 이름, add/drop은 인덱스를 만들고 지우는 DDL이다.
writer가 있으면 lockprobe로 잠금 범위와 다른 연결의 쓰기 대기 시간도 잰다.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import json
import statistics
import subprocess
import sys


from qtime import run  # noqa: E402

CASES = {
    "basecamp-list": dict(before="recruiting", after="recruiting", add=["ALTER TABLE basecamp ADD INDEX idx_basecamp_status_start_date (status, start_date)"], drop=["ALTER TABLE basecamp DROP INDEX idx_basecamp_status_start_date"]),
    "basecamp-departure": dict(before="departure", after="departure", add=["ALTER TABLE basecamp ADD INDEX idx_basecamp_status_start_date (status, start_date)"], drop=["ALTER TABLE basecamp DROP INDEX idx_basecamp_status_start_date"]),
    "basecamp-completion": dict(before="completion", after="completion", add=["ALTER TABLE basecamp ADD INDEX idx_basecamp_status_start_date (status, start_date)"], drop=["ALTER TABLE basecamp DROP INDEX idx_basecamp_status_start_date"]),
    "basecamp-sanction": dict(before="sanction", after="sanction-new2", add=[], drop=[]),
    "basecamp-contact": dict(before="contact", after="contact-byid", pre_after="contact-select", writer="w-basecamp",
                             add=["ALTER TABLE basecamp ADD INDEX idx_basecamp_contact_info (contact_info(1))"], drop=["ALTER TABLE basecamp DROP INDEX idx_basecamp_contact_info"]),
    "ev-del": dict(before="ev-del", after="ev-del", writer="w-ev", add=["ALTER TABLE email_verification ADD INDEX idx_email_verification_created_at (created_at)"], drop=["ALTER TABLE email_verification DROP INDEX idx_email_verification_created_at"]),
    "prt-del": dict(before="prt-del", after="prt-del", writer="w-prt", add=["ALTER TABLE password_reset_token ADD INDEX idx_password_reset_token_expires_at (expires_at)"], drop=["ALTER TABLE password_reset_token DROP INDEX idx_password_reset_token_expires_at"]),
    "ci-del": dict(before="ci-del", after="ci-del", writer="w-ci", add=["ALTER TABLE identity_verification ADD INDEX idx_identity_verification_ci_retained_until (ci_retained_until)"], drop=["ALTER TABLE identity_verification DROP INDEX idx_identity_verification_ci_retained_until"]),
    "ob-del": dict(before="ob-del", after="ob-del", writer="w-ob", add=["ALTER TABLE outbox_event ADD INDEX idx_outbox_event_status_published_at (status, published_at)"], drop=["ALTER TABLE outbox_event DROP INDEX idx_outbox_event_status_published_at"]),
    "sx": dict(before="sx", after="sx", writer="w-sx", add=["ALTER TABLE sanction ADD INDEX idx_sanction_status_ends_at (status, ends_at)"], drop=["ALTER TABLE sanction DROP INDEX idx_sanction_status_ends_at"]),
    "adm-comments": dict(before="adm-comments", after="adm-comments-new", add=["ALTER TABLE community_comment ADD INDEX idx_community_comment_status (status)"], drop=["ALTER TABLE community_comment DROP INDEX idx_community_comment_status"]),
    "adm-posts": dict(before="adm-posts", after="adm-posts-new", add=[], drop=[]),
    "adm-memories": dict(before="adm-memories", after="adm-memories-new", add=["ALTER TABLE memory ADD INDEX idx_memory_status (status)"], drop=["ALTER TABLE memory DROP INDEX idx_memory_status"]),
    "adm-spots": dict(before="adm-spots", after="adm-spots-new", add=[], drop=[]),
    "adm-applicants": dict(before="adm-applicants-big", after="adm-applicants-hint-big", add=[], drop=[]),
    "cr-clear": dict(before="cr-clear", after="cr-clear-byid", pre_after="cr-clear-select", writer="w-cr", add=[], drop=[]),
    "mmr-clear": dict(before="mmr-clear", after="mmr-clear-byid", pre_after="mmr-clear-select", add=[], drop=[]),
    "br-clear": dict(before="br-clear", after="br-clear-byid", pre_after="br-clear-select", add=[], drop=[]),
    "mr-clear": dict(before="mr-clear", after="mr-clear-byid", pre_after="mr-clear-select", add=[], drop=[]),
}


def ddl(stmts):
    for s in stmts:
        p = subprocess.run([S + "/sql.sh", "--batch"], input=(s + ";").encode(), capture_output=True)
        if p.returncode:
            raise RuntimeError(p.stderr.decode())


def sql_of(name):
    return open(f"{D}/ab/{name}.sql").read()


def probe(stmt_name, writer):
    p = subprocess.run([S + "/lockprobe.sh", f"{D}/ab/{stmt_name}.sql", f"{D}/ab/{writer}.sql"], capture_output=True)
    out = dict(ln.split(" ") for ln in p.stdout.decode().strip().split("\n") if " " in ln)
    return int(out.get("row_locks", 0)), float(out.get("writer_wait_s", 0))


def main():
    name = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    c = CASES[name]
    res = {"before": [], "after": []}
    for i in range(n):
        for side in ("before", "after"):
            if side == "after":
                ddl(c["add"])
            r = run(sql_of(c[side]), 1)[0]
            pre = run(sql_of(c["pre_after"]), 1)[0] if side == "after" and c.get("pre_after") else None
            rec = dict(ms=r[0] + (pre[0] if pre else 0), examined=r[1] + (pre[1] if pre else 0), sent=r[2], affected=r[3])
            if c.get("writer") and i == 0:
                rec["row_locks"], rec["writer_wait_s"] = probe(c[side], c["writer"])
            res[side].append(rec)
            if side == "after":
                ddl(c["drop"])
    json.dump(res, open(f"{D}/ab/{name}.json", "w"), indent=1)
    for side in ("before", "after"):
        ms = [x["ms"] for x in res[side]]
        lock = res[side][0]
        extra = f", 잠금 {lock['row_locks']}행, 쓰기 대기 {lock['writer_wait_s']}초" if "row_locks" in lock else ""
        print(f"{name} {side}: 중앙값 {statistics.median(ms):.2f} ms (범위 {min(ms):.2f}~{max(ms):.2f}, {n}회), 읽은 행 {res[side][-1]['examined']}, 돌려준 행 {res[side][-1]['sent']}, 바꾼 행 {res[side][-1]['affected']}{extra}")


if __name__ == "__main__":
    main()
