#!/usr/bin/env python3
"""측정 결과를 표로 합치고 기준을 넘은 항목을 찾는다.

  summary.py        out/api.tsv, out/query.tsv, out/findings.tsv를 만들고 SUMMARY.md의 표 부분을 다시 쓴다

기준: 단건 p95 200ms 초과, 부하 p95 1초 초과, 읽은 행이 돌려준 행의 100배 초과, 잠금을 쥔 채 전체 스캔하는 UPDATE/DELETE.
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

from sweep import slug  # noqa: E402
from explain import bind  # noqa: E402


def with_literals(sql):
    """STATEMENT_DIGEST는 자리표시자를 읽지 못한다. 값은 다이제스트에서 다시 ?로 바뀌므로 아무 숫자나 넣는다."""
    return bind(sql, ["1"] * sql.count("?")) or sql

BEGIN = "<!-- 표 시작: summary.py가 다시 쓴다 -->"
END = "<!-- 표 끝 -->"


def ledger(prefix):
    with open(LEDGER) as f:
        return [r for r in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE) if r["id"].startswith(prefix)]


def statement_digests(sqls):
    """SQL 원문마다 MySQL의 STATEMENT_DIGEST를 구한다. 결과를 파일에 남겨 MySQL이 꺼져 있어도 표를 만든다."""
    path = OUT + "/sql-digest.json"
    known = json.load(open(path)) if os.path.exists(path) else {}
    todo = [s for s in sqls if s not in known]
    chunk = 200
    for i in range(0, len(todo), chunk):
        batch = todo[i: i + chunk]
        script = []
        for s in batch:
            q = with_literals(s).replace("\\", "\\\\").replace("'", "''")
            script.append(f"SELECT STATEMENT_DIGEST('{q}');")
        p = subprocess.run([S + "/sql.sh", "--batch", "--skip-column-names", "--force"], input="\n".join(script).encode(), capture_output=True)
        lines = p.stdout.decode().strip().split("\n")
        if len(lines) == len(batch):
            known.update(dict(zip(batch, lines)))
        else:
            for s in batch:
                q = with_literals(s).replace("\\", "\\\\").replace("'", "''")
                p = subprocess.run([S + "/sql.sh", "--batch", "--skip-column-names", "--force"], input=f"SELECT STATEMENT_DIGEST('{q}');".encode(), capture_output=True)
                v = p.stdout.decode().strip()
                known[s] = v if v else ""
    json.dump(known, open(path, "w"), ensure_ascii=False)
    return known


def load_maps():
    stmts, by_trace, jobs = {}, {}, {}
    for f in sorted(glob.glob(OUT + "/map-*.json")):
        m = json.load(open(f))
        for sid, sqls in m["stmts"].items():
            d = stmts.setdefault(sid, {})
            for sql, st in sqls.items():
                d[sql] = d.get(sql, 0) + st["count"]
        for tr, ids in m["by_trace"].items():
            by_trace.setdefault(tr, []).extend(ids)
        for th, ids in m["jobs"].items():
            jobs.setdefault(th, []).extend(ids)
    return stmts, by_trace, jobs


def load_digests():
    res = {}
    for f in sorted(glob.glob(OUT + "/digest-*.tsv")):
        if os.path.basename(f).startswith("digest-debug"):
            continue
        with open(f) as fh:
            for r in csv.DictReader(fh, delimiter="\t", quoting=csv.QUOTE_NONE, escapechar="\\"):
                k = r["DIGEST"]
                x = res.setdefault(k, dict(count=0, no_index=0, no_good=0, merge=0, sort_rows=0, tmp=0, tmp_disk=0, examined=0, sent=0, affected=0, total_ms=0.0, max_ms=0.0, text=r["DIGEST_TEXT"]))
                c = int(r["COUNT_STAR"])
                x["count"] += c
                x["no_index"] += int(r["SUM_NO_INDEX_USED"])
                x["no_good"] += int(r["SUM_NO_GOOD_INDEX_USED"])
                x["merge"] += int(r["SUM_SORT_MERGE_PASSES"])
                x["sort_rows"] += int(r["SUM_SORT_ROWS"])
                x["tmp"] += int(r["SUM_CREATED_TMP_TABLES"])
                x["tmp_disk"] += int(r["SUM_CREATED_TMP_DISK_TABLES"])
                x["examined"] += int(r["SUM_ROWS_EXAMINED"])
                x["sent"] += int(r["SUM_ROWS_SENT"])
                x["affected"] += int(r["SUM_ROWS_AFFECTED"])
                x["total_ms"] += float(r["avg_ms"]) * c
                x["max_ms"] = max(x["max_ms"], float(r["max_ms"]))
    return res


def cell(v, f="{:.1f}"):
    return f.format(v) if isinstance(v, (int, float)) else (v if v else "-")


def main():
    stmts, by_trace, jobs = load_maps()
    all_sql = sorted({s for d in stmts.values() for s in d})
    dig = statement_digests(all_sql)
    digests = load_digests()
    id_digests = {}
    digest_ids = {}
    for sid, d in stmts.items():
        for sql in d:
            k = dig.get(sql)
            if k:
                id_digests.setdefault(sid, set()).add(k)
                digest_ids.setdefault(k, set()).add(sid)

    smoke = {}
    for f in glob.glob(OUT + "/smoke*.json"):
        for x in json.load(open(f)):
            if isinstance(x.get("status"), int) and 200 <= x["status"] < 300:
                smoke.setdefault(x["api"], []).append(x)
    queries = {}
    if os.path.exists(OUT + "/queries.jsonl"):
        for ln in open(OUT + "/queries.jsonl"):
            x = json.loads(ln)
            queries[x["id"]] = x

    api_rows, q_rows, findings = [], [], []
    for row in ledger("API:"):
        key = row["item"]
        def rd(p):
            f = f"{OUT}/{p}/{slug(key)}.json"
            return json.load(open(f)) if os.path.exists(f) else None
        seq, load = rd("seq"), rd("load")
        ids = []
        for s in smoke.get(key, []):
            for i in by_trace.get(s.get("trace", ""), []):
                if i not in ids:
                    ids.append(i)
        def codes(r):
            return {k: v for k, v in (r or {}).get("statuses", {}).items() if v} if r else {}
        def bad(r):
            ok = {str(c) for c in (r or {}).get("okcodes", [])}
            return sum(v for k, v in codes(r).items() if not k.startswith("2"))
        api_rows.append(dict(
            api=key,
            seq_n=(seq or {}).get("count", 0),
            seq_p50=((seq or {}).get("ok") or {}).get("med"),
            seq_p95=((seq or {}).get("ok") or {}).get("p(95)"),
            seq_max=((seq or {}).get("ok") or {}).get("max"),
            seq_bad=bad(seq),
            load_n=(load or {}).get("count", 0),
            load_p95=((load or {}).get("ok") or {}).get("p(95)"),
            load_rps=(load or {}).get("rate"),
            load_bad=bad(load),
            seq_codes=json.dumps(codes(seq)),
            load_codes=json.dumps(codes(load)),
            smoke_2xx=key in smoke,
            queries=" ".join(ids),
        ))
    for r in api_rows:
        if r["seq_p95"] and r["seq_p95"] > 200:
            findings.append(("단건 200ms 초과", r["api"], f"p50 {r['seq_p50']:.1f}, p95 {r['seq_p95']:.1f}, 최대 {r['seq_max']:.1f}"))
        if r["load_p95"] and r["load_p95"] > 1000:
            findings.append(("부하 p95 1초 초과", r["api"], f"p95 {r['load_p95']:.1f}, 처리량 {r['load_rps']:.1f}/초"))
        if r["seq_bad"] or r["load_bad"]:
            findings.append(("2xx 아닌 응답", r["api"], f"혼자 {r['seq_codes']} 부하 {r['load_codes']}"))

    for row in ledger("QUERY:"):
        qid = row["id"][6:]
        ks = id_digests.get(qid, set())
        ds = [digests[k] for k in ks if k in digests]
        tot = {f: sum(d[f] for d in ds) for f in ("count", "no_index", "no_good", "merge", "tmp_disk", "examined", "sent", "affected")}
        total_ms = sum(d["total_ms"] for d in ds)
        q = queries.get(qid, {})
        types = ",".join(f"{t['table']}:{t['type']}" + (f"({t.get('rows')})" if t.get("rows") else "") for t in q.get("tables", []))
        flags = ("filesort " if q.get("filesort") else "") + ("temp" if q.get("temp") else "")
        rec = dict(
            id=qid,
            source=q.get("source", "없음"),
            plan=types,
            flags=flags.strip(),
            examined=q.get("examined"),
            sent=q.get("sent"),
            affected=q.get("affected"),
            ms=q.get("ms"),
            digest_count=tot["count"],
            d_examined=tot["examined"],
            d_sent=tot["sent"],
            d_affected=tot["affected"],
            no_index=tot["no_index"],
            merge=tot["merge"],
            tmp_disk=tot["tmp_disk"],
            avg_ms=round(total_ms / tot["count"], 3) if tot["count"] else None,
            max_ms=max((d["max_ms"] for d in ds), default=None),
            error=q.get("error", ""),
        )
        q_rows.append(rec)
        ex = int(q.get("examined") or 0) if str(q.get("examined") or "").isdigit() else 0
        snt = int(q.get("sent") or 0) if str(q.get("sent") or "").isdigit() else 0
        aff = int(q.get("affected") or 0) if str(q.get("affected") or "").isdigit() else 0
        back = max(snt, aff, 1)
        d_ratio = (tot["examined"] / max(tot["sent"] + tot["affected"], 1)) if tot["count"] else 0
        sqltext = q.get("sql", "") or ""
        write = re.match(r"\s*(update|delete)\b", sqltext, re.I)
        full = [t for t in q.get("tables", []) if t["type"] in ("ALL", "index")]
        if write and full:
            findings.append(("쓰기 전체 스캔", qid, f"{types}, 읽음 {ex}, 바꿈 {aff}"))
        if ex / back > 100 or d_ratio > 100:
            findings.append(("읽은 행 100배 초과", qid, f"직접 {ex}/{back}, 일괄 {tot['examined']}/{tot['sent'] + tot['affected']}, {types} {flags}"))
        if tot["merge"] or tot["tmp_disk"]:
            findings.append(("디스크 정렬·임시표", qid, f"merge {tot['merge']}, tmp_disk {tot['tmp_disk']}, 횟수 {tot['count']}"))

    extra = []
    for k, d in digests.items():
        ids = digest_ids.get(k)
        if ids and any(not i.startswith("JPA:") for i in ids):
            continue
        extra.append(dict(ids=",".join(sorted(ids or [])), **{f: d[f] for f in ("count", "no_index", "merge", "tmp_disk", "examined", "sent", "affected")}, avg_ms=round(d["total_ms"] / max(d["count"], 1), 3), max_ms=d["max_ms"], text=d["text"][:220]))
    extra.sort(key=lambda x: -x["examined"])

    def dump(name, rows):
        if rows:
            with open(f"{OUT}/{name}.tsv", "w") as o:
                w = csv.DictWriter(o, rows[0].keys(), delimiter="\t", quoting=csv.QUOTE_MINIMAL)
                w.writeheader()
                w.writerows(rows)
    dump("api", api_rows)
    dump("query", q_rows)
    dump("extra-digest", extra)
    with open(OUT + "/findings.tsv", "w") as o:
        for f in findings:
            o.write("\t".join(f) + "\n")

    md = [BEGIN, "", "### API (시간 ms, 2xx만)", "", "| API | 단건 n | 단건 p50 | 단건 p95 | 부하 n | 부하 p95 | 부하 처리량/초 | 2xx 아님(단건/부하) | 관여 쿼리 |", "|---|---:|---:|---:|---:|---:|---:|---:|---|"]
    for r in api_rows:
        md.append(f"| `{r['api']}` | {r['seq_n']} | {cell(r['seq_p50'])} | {cell(r['seq_p95'])} | {r['load_n']} | {cell(r['load_p95'])} | {cell(r['load_rps'])} | {r['seq_bad']}/{r['load_bad']} | {r['queries'][:300] or '-'} |")
    md += ["", "### 쿼리 (직접은 대표 값 한 번, 일괄은 부하 전체 합)", "", "| 쿼리 | SQL 출처 | 실행계획 | 정렬·임시 | 직접 읽음 | 직접 돌려줌/바꿈 | 직접 ms | 일괄 횟수 | 일괄 읽음 | 일괄 돌려줌+바꿈 | 인덱스 없음 | merge | 디스크 임시표 | 평균 ms | 최대 ms |", "|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for r in q_rows:
        md.append(f"| `{r['id']}` | {r['source']} | {r['plan'][:160] or '-'} | {r['flags'] or '-'} | {r['examined'] or '-'} | {r['sent'] or '-'}/{r['affected'] or '-'} | {r['ms'] or '-'} | {r['digest_count']} | {r['d_examined']} | {r['d_sent'] + r['d_affected']} | {r['no_index']} | {r['merge']} | {r['tmp_disk']} | {r['avg_ms'] or '-'} | {r['max_ms'] or '-'} |")
    md += ["", END]
    path = D + "/SUMMARY.md"
    head = ""
    if os.path.exists(path):
        s = open(path).read()
        if BEGIN in s:
            head, tail = s.split(BEGIN)[0], s.split(END)[1]
            open(path, "w").write(head + "\n".join(md) + tail)
        else:
            open(path, "a").write("\n" + "\n".join(md) + "\n")
    else:
        open(path, "w").write("# 전체 API·쿼리 측정\n\n" + "\n".join(md) + "\n")

    print(f"API {len(api_rows)}, 2xx 확인 {sum(1 for r in api_rows if r['smoke_2xx'])}, 단건 잰 것 {sum(1 for r in api_rows if r['seq_p95'])}, 부하 잰 것 {sum(1 for r in api_rows if r['load_p95'])}")
    print(f"쿼리 {len(q_rows)}, 다이제스트 맞음 {sum(1 for r in q_rows if r['digest_count'])}, 직접 잰 것 {sum(1 for r in q_rows if r['examined'] is not None)}, 실패 {sum(1 for r in q_rows if r['error'])}")
    print(f"발견 {len(findings)}, 매퍼 밖 다이제스트 {len(extra)}")


if __name__ == "__main__":
    main()
