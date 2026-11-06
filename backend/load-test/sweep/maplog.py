#!/usr/bin/env python3
"""DEBUG 로그에서 쿼리 id와 실제 SQL, 파라미터, 추적 ID를 뽑는다.

  docker logs 앱 | maplog.py <이름>   out/map-<이름>.json을 만든다

MyBatis는 logger가 매퍼 메서드 이름이고, Hibernate는 hibernate.use_sql_comments로 JPQL을 SQL 앞에 주석으로 붙인다.
JPQL 주석을 @Query 원문과 맞춰 Repository 메서드를 찾는다. @Query가 아닌 저장·조회는 JPA:표 이름으로 남긴다.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import glob
import json
import os
import re
import sys

J = ROOT + "/backend/src/main/java"


def norm(s):
    return re.sub(r"\s+", " ", s).strip()


def canon(s):
    """Hibernate가 JPQL 공백을 다르게 쓰고 네이티브 SQL은 그대로 쓰므로 공백과 대소문자를 빼고 맞춘다."""
    return re.sub(r"\s+", "", s).lower()


# 장부 도구의 정규식이 @Query가 아닌 파생 메서드나 JPQL 안의 함수 이름을 메서드로 읽은 행이다. 실제 JPQL과 이어 준다.
LEDGER_ALIAS = {
    canon("SELECT COUNT(m) > 0 FROM Member m WHERE m.nickname = :__$synthetic$__1"): "MemberJpaRepository.existsByNickname",
    canon("SELECT COUNT(m) > 0 FROM Member m WHERE m.nickname = :__$synthetic$__1 AND m.id <> :__$synthetic$__2"): "MemberJpaRepository.existsByNicknameAndIdNot",
    canon("SELECT new com.moewadeul.member.domain.verification.SendWindow(COUNT(e), MIN(e.createdAt)) FROM EmailVerification e WHERE e.memberId = :memberId AND e.createdAt > :since"): "EmailVerificationJpaRepository.MIN",
    canon("SELECT new com.moewadeul.member.domain.verification.SendWindow(COUNT(e), MIN(e.createdAt)) FROM EmailVerification e WHERE e.requestIp = :requestIp AND e.createdAt > :since"): "EmailVerificationJpaRepository.MIN",
}


def jpql_index():
    idx = {}
    for f in glob.glob(J + "/**/*Repository.java", recursive=True):
        s = open(f).read()
        n = os.path.basename(f)[:-5]
        for m in re.finditer(r"@Query\s*\((.*?)\)\s*\n\s*(?:@\w+(?:\([^)]*\))?\s*)*[\w<>\[\], .?]+\s+(\w+)\(", s, re.S):
            parts = re.findall(r'"""(.*?)"""|"((?:[^"\\]|\\.)*)"', m.group(1), re.S)
            text = norm(" ".join(a or b for a, b in parts))
            # 같은 JPQL을 잠금 방식만 달리해 여러 메서드가 쓴다. 후보를 모아 두고 SQL 끝의 잠금으로 고른다.
            idx.setdefault(canon(text), []).append(f"{n}.{m.group(2)}")
            # 네이티브 쿼리는 주석에 원문이 없으니 자리표시자로 바꾼 SQL로 찾는다.
            idx.setdefault(canon(re.sub(r":\w+", "?", text)), []).append(f"{n}.{m.group(2)}")
    for k, v in LEDGER_ALIAS.items():
        idx[k] = [v]
    return idx


def pick(cands, sql):
    s = sql.lower()
    for word, tag in (("for update", "ForUpdate"), ("for share", "ForShare")):
        if s.rstrip().endswith(word):
            hit = [c for c in cands if tag in c]
            if hit:
                return hit[0]
    plain = [c for c in cands if "ForUpdate" not in c and "ForShare" not in c]
    return (plain or cands)[0]


def main():
    name = sys.argv[1]
    idx = jpql_index()
    stmts = {}
    by_trace = {}
    jobs = {}
    pending = {}  # thread -> (id, sql)
    unknown_jpql = {}
    lines = 0
    for line in sys.stdin:
        if not line.startswith("{"):
            continue
        try:
            e = json.loads(line)
        except ValueError:
            continue
        lines += 1
        lg = e.get("log", {})
        logger = lg.get("logger", "") if isinstance(lg, dict) else ""
        msg = e.get("message", "")
        th = e.get("process", {}).get("thread", {}).get("name", "")
        tr = e.get("traceId", "")
        sid, sql = None, None
        if logger.startswith("com.moewadeul") and msg.startswith("==>  Preparing:"):
            parts = logger.split(".")
            sid = ".".join(parts[-2:])
            sql = norm(msg[len("==>  Preparing:"):])
        elif logger.startswith("com.moewadeul") and msg.startswith("==> Parameters:"):
            p = pending.get(th)
            if p:
                st = stmts[p[0]][p[1]]
                if st["params"] is None:
                    st["params"] = msg[len("==> Parameters:"):].strip()
            continue
        elif logger == "org.hibernate.SQL":
            full = norm(msg)
            c = re.match(r"/\*\s*(.*?)\s*\*/\s*(.*)", full)
            text = c.group(1) if c else ""
            sql = c.group(2) if c else full
            if canon(text) in idx:
                sid = pick(idx[canon(text)], sql)
            elif canon(sql) in idx:
                sid = pick(idx[canon(sql)], sql)
            elif sql.lower().split(" ")[0] in ("select", "insert", "update", "delete"):
                t = re.search(r"\b(?:from|into|update)\s+`?(\w+)", sql, re.I)
                sid = f"JPA:{sql.split(' ')[0].lower()} {t.group(1) if t else '?'}"
                if text and not text.startswith(("insert", "update", "delete", "<criteria>", "load", "dynamic")) and "select" in text.lower():
                    unknown_jpql[text[:200]] = sid
            pending.pop(th, None)
        elif logger == "org.hibernate.orm.jdbc.bind":
            p = pending.get("jpa:" + th)
            if p:
                st = stmts[p[0]][p[1]]
                m = re.search(r"\((\d+):(\w+)\) <- \[(.*)\]", msg)
                if m:
                    if st["binds_open"]:
                        st["binds"].append([m.group(2), m.group(3)])
            continue
        else:
            continue
        if not sid:
            continue
        s = stmts.setdefault(sid, {}).setdefault(sql, dict(count=0, params=None, binds=[], binds_open=True, threads=[]))
        s["count"] += 1
        if s["count"] > 1:
            s["binds_open"] = False
        if th not in s["threads"] and len(s["threads"]) < 8:
            s["threads"].append(th)
        pending[th] = (sid, sql)
        if logger == "org.hibernate.SQL":
            for k, v in list(stmts.items()):
                pass
            pending["jpa:" + th] = (sid, sql)
        if tr:
            by_trace.setdefault(tr, [])
            if sid not in by_trace[tr]:
                by_trace[tr].append(sid)
        else:
            jobs.setdefault(th, [])
            if sid not in jobs[th]:
                jobs[th].append(sid)
    out = dict(stmts=stmts, by_trace=by_trace, jobs=jobs, unknown_jpql=unknown_jpql, jpql_ids=sorted({v for vs in idx.values() for v in vs}))
    json.dump(out, open(f"{D}/out/map-{name}.json", "w"), ensure_ascii=False, indent=0)
    print(f"줄 {lines}, 문장 id {len(stmts)}, 추적 {len(by_trace)}, 모르는 JPQL {len(unknown_jpql)}")


if __name__ == "__main__":
    main()
