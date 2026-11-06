#!/usr/bin/env python3
"""장부의 QUERY: 행마다 실행계획과 읽은·돌려준 행을 잰다.

실제로 돈 쿼리는 로그에 남은 SQL과 값(map-*.json)을 쓰고, 돌지 않은 쿼리는 render.py가 매퍼 XML에서 만든 SQL을 쓴다.
쓰기 문장은 트랜잭션 안에서 실행하고 되돌린다. 읽은 행과 시간은 performance_schema의 문장 기록에서 읽는다.

  explain.py          out/queries.jsonl을 만든다(이미 있는 id는 건너뛴다. FORCE=1이면 다시)
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import csv
import glob
import json
import os
import re
import subprocess
import sys

OUT = D + "/out"

TYPES = r"Long|Integer|Int|String|Timestamp|Boolean|Double|BigDecimal|LocalDate|LocalDateTime|Instant|Date|Short|Byte|Float|OffsetDateTime|ZonedDateTime"
STAT = ("SELECT '@@STAT', ROWS_EXAMINED, ROWS_SENT, ROWS_AFFECTED, NO_INDEX_USED, NO_GOOD_INDEX_USED, SORT_MERGE_PASSES, SORT_ROWS, CREATED_TMP_TABLES, CREATED_TMP_DISK_TABLES, "
        "ROUND(TIMER_WAIT/1e9,3), ROUND(LOCK_TIME/1e9,3), MYSQL_ERRNO FROM performance_schema.events_statements_history WHERE THREAD_ID=PS_CURRENT_THREAD_ID() ORDER BY EVENT_ID DESC LIMIT 1;")


def ledger_queries():
    with open(LEDGER) as f:
        return [r for r in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE) if r["id"].startswith("QUERY:")]


def lit(v, typ):
    if v == "null" or v is None:
        return "NULL"
    if typ in ("Long", "Integer", "Int", "Double", "BigDecimal", "Short", "Byte", "Float", "BIGINT", "INTEGER", "DOUBLE", "SMALLINT", "TINYINT", "FLOAT", "DECIMAL"):
        return v
    if typ in ("Boolean", "BOOLEAN", "BIT"):
        return "1" if v in ("true", "1") else "0"
    if re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d(:\d\d(\.\d+)?)?(Z|[+-]\d\d:\d\d)?", v):
        v = re.sub(r"(Z|[+-]\d\d:\d\d)$", "", v).replace("T", " ")
    return "'" + v.replace("\\", "\\\\").replace("'", "''") + "'"


def parse_mybatis(params):
    out, s = [], params or ""
    while s:
        if s.startswith("null"):
            out.append(("null", None))
            s = s[4:]
        else:
            m = re.match(rf"(.*?)\(({TYPES})\)(?=, |$)", s, re.S)
            if not m:
                return None
            out.append((m.group(1), m.group(2)))
            s = s[m.end():]
        if s.startswith(", "):
            s = s[2:]
    return out


def bind(sql, values):
    """문자열 안의 ?는 건드리지 않고 자리표시자에만 값을 넣는다."""
    res, i, n = [], 0, 0
    in_s = None
    while i < len(sql):
        c = sql[i]
        if in_s:
            res.append(c)
            if c == in_s:
                in_s = None
        elif c in "'\"`":
            in_s = c
            res.append(c)
        elif c == "?":
            if n >= len(values):
                return None
            res.append(values[n])
            n += 1
        else:
            res.append(c)
        i += 1
    return "".join(res) if n == len(values) else None


def logged_source():
    best = {}
    for f in sorted(glob.glob(OUT + "/map-*.json")):
        m = json.load(open(f))
        for sid, sqls in m["stmts"].items():
            for sql, st in sqls.items():
                if st.get("binds"):
                    vals = [lit(v, t) for t, v in st["binds"]]
                else:
                    p = parse_mybatis(st.get("params"))
                    vals = [lit(v, t) for v, t in p] if p is not None else None
                if vals is None:
                    continue
                lit_sql = bind(sql, vals)
                if lit_sql:
                    best.setdefault(sid, []).append((sql, lit_sql))
    return best


def walk(n, acc):
    if isinstance(n, dict):
        if "table_name" in n and "access_type" in n:
            acc["tables"].append(dict(table=n["table_name"], type=n["access_type"], key=n.get("key"), rows=n.get("rows_examined_per_scan"), filtered=n.get("filtered")))
        if n.get("using_filesort"):
            acc["filesort"] = True
        if n.get("using_temporary_table"):
            acc["temp"] = True
        for v in n.values():
            walk(v, acc)
    elif isinstance(n, list):
        for v in n:
            walk(v, acc)


def is_select(sql):
    return re.match(r"\s*\(?\s*(select|with)\b", sql, re.I) is not None


def run(sql):
    s = sql.strip().rstrip(";")
    script = ["SET SESSION innodb_lock_wait_timeout=10;", "SELECT '@@PLAN';", f"EXPLAIN FORMAT=JSON {s};"]
    if is_select(s):
        script += ["SELECT '@@ANALYZE';", f"EXPLAIN ANALYZE {s};"]
    script += ["SELECT '@@RUN';", "START TRANSACTION;", f"{s};", STAT, "ROLLBACK;"]
    env_cmd = [S + "/sql.sh", "--batch", "--raw", "--skip-column-names", "--force"]
    p = subprocess.run(env_cmd, input="\n".join(script).encode(), capture_output=True, timeout=600)
    text = p.stdout.decode(errors="replace")
    err = "\n".join(ln for ln in p.stderr.decode(errors="replace").split("\n") if "ERROR" in ln)[:400]
    plan = text.split("@@PLAN\n", 1)[1].split("@@", 1)[0] if "@@PLAN\n" in text else ""
    an = text.split("@@ANALYZE\n", 1)[1].split("@@RUN", 1)[0] if "@@ANALYZE\n" in text else ""
    stat = None
    m = re.search(r"@@STAT\t(.*)", text)
    if m:
        stat = m.group(1).split("\t")
    r = dict(error=err)
    try:
        acc = dict(tables=[], filesort=False, temp=False)
        walk(json.loads(plan), acc)
        r.update(acc)
    except ValueError:
        r["tables"] = []
    r["analyze"] = an[:1600]
    if stat:
        keys = "examined sent affected no_index no_good_index merge_passes sort_rows tmp tmp_disk ms lock_ms errno".split()
        r.update(dict(zip(keys, stat)))
    return r


def main():
    logged = logged_source()
    rendered = {}
    if os.path.exists(OUT + "/rendered.json"):
        rendered = json.load(open(OUT + "/rendered.json"))
    done = {}
    path = OUT + "/queries.jsonl"
    if os.path.exists(path) and not os.environ.get("FORCE"):
        for ln in open(path):
            x = json.loads(ln)
            done[x["id"]] = x
    only = sys.argv[1] if len(sys.argv) > 1 else None
    with open(path, "a" if done else "w") as o:
        for row in ledger_queries():
            qid = row["id"][6:]
            if qid in done or (only and only not in qid):
                continue
            # 동적 SQL은 대표 조건 하나만 잰다. 로그에 여러 모양이 있으면 가장 긴 문장(조건이 많은 쪽)을 고른다.
            src = None
            if qid in logged:
                sql, lit_sql = max(logged[qid], key=lambda x: len(x[0]))
                src = "logged"
            elif qid in rendered:
                sql, lit_sql = rendered[qid], rendered[qid]
                src = "rendered"
            if not src:
                x = dict(id=qid, source="none", error="SQL을 얻지 못함")
            else:
                x = dict(id=qid, source=src, sql=lit_sql[:4000], **run(lit_sql))
            o.write(json.dumps(x, ensure_ascii=False) + "\n")
            o.flush()
            t = ",".join(f"{a['table']}:{a['type']}" for a in x.get("tables", []))
            print(qid, src, t, "ex", x.get("examined"), "sent", x.get("sent"), "aff", x.get("affected"), "ms", x.get("ms"), x.get("error", "")[:120], flush=True)


if __name__ == "__main__":
    main()
