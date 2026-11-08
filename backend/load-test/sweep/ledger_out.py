#!/usr/bin/env python3
"""측정 결과로 장부 조각 ledger.d/sweep.tsv를 만든다.

API는 혼자 재기와 50 VU 재기에서 2xx를 받았으면 잼, 받지 못했으면 열림이다. 쿼리는 실행계획과 읽은·돌려준 행을 쟀으면 잼이다.
다른 줄이 이미 닫은 행(고침·제외 등)은 건드리지 않는다. 기준을 넘은 문제는 GAP:성능-<이름> 행으로 더한다(findings.py가 정한 목록).
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import csv
import glob
import json
import os
import sys

TOOLS = os.path.dirname(LEDGER)
OUT = TOOLS + "/ledger.d/sweep.tsv"
COLS = ["id", "area", "item", "source", "risk", "status", "verdict", "evidence", "commit", "rerun"]
EVIDENCE = "backend/load-test/results/2026-11-06-sweep/SUMMARY.md"
COMMIT = "80b558c2"
RERUN = "backend/load-test/sweep/all.sh"
TAG = "잠금 없이 동시 측정, 찾기용"


def others():
    rows = {r["id"]: r for r in csv.DictReader(open(TOOLS + "/ledger.tsv"), delimiter="\t", quoting=csv.QUOTE_NONE)}
    for f in sorted(glob.glob(TOOLS + "/ledger.d/*.tsv")):
        if f == OUT:
            continue
        for r in csv.DictReader(open(f), delimiter="\t", quoting=csv.QUOTE_NONE):
            base = rows.get(r["id"], dict.fromkeys(COLS, ""))
            rows[r["id"]] = {k: (r.get(k) or base.get(k) or "") for k in COLS}
    return rows


def f1(v):
    return f"{float(v):.1f}" if v not in (None, "") else "-"


def clean(s):
    return s.replace("\t", " ").replace("\n", " ")


def main():
    base = others()
    api = {r["api"]: r for r in csv.DictReader(open(D + "/out/api.tsv"), delimiter="\t")}
    qry = {r["id"]: r for r in csv.DictReader(open(D + "/out/query.tsv"), delimiter="\t")}
    gaps = json.load(open(D + "/out/gaps.json")) if os.path.exists(D + "/out/gaps.json") else {}
    notes = json.load(open(D + "/out/notes.json")) if os.path.exists(D + "/out/notes.json") else {}
    out, skipped = [], []
    for rid, r in base.items():
        if rid.startswith("API:"):
            a = api.get(r["item"])
            if not a:
                continue
            if r["status"] not in ("열림", ""):
                skipped.append(rid)
                continue
            seq_ok = a["seq_p95"] not in ("", None)
            if not seq_ok:
                status, verdict = "열림", "2xx를 받지 못해 재지 못했다"
            else:
                load = f"50 VU p95 {f1(a['load_p95'])}ms, 초당 {f1(a['load_rps'])}건, 2xx 아님 {a['load_bad']}/{a['load_n']}" if a["load_p95"] else "부하는 재지 않음(" + notes.get(r["item"], "사유 SUMMARY") + ")"
                verdict = f"{TAG}. 혼자 p50 {f1(a['seq_p50'])}ms p95 {f1(a['seq_p95'])}ms(n={a['seq_n']}, 2xx 아님 {a['seq_bad']}), {load}. 관여 쿼리 {len(a['queries'].split())}개"
                g = gaps.get(r["item"])
                if g:
                    verdict += f". 기준 초과: {g}"
                status = "잼"
            out.append(dict(id=rid, status=status, verdict=clean(verdict), evidence=EVIDENCE, commit=COMMIT, rerun=RERUN))
        elif rid.startswith("QUERY:"):
            q = qry.get(rid[6:])
            if not q:
                continue
            if r["status"] not in ("열림", ""):
                skipped.append(rid)
                continue
            plan = q["plan"] or "계획 없음"
            verdict = (f"{TAG}. 실행계획 {plan[:160]}{(' ' + q['flags']) if q['flags'] else ''}, 직접 실행 읽음 {q['examined'] or '-'} 돌려줌 {q['sent'] or '-'} 바꿈 {q['affected'] or '-'} ({q['source']} SQL), "
                       f"일괄 실행 중 {q['digest_count']}회 읽음 {q['d_examined']} 돌려줌·바꿈 {int(q['d_sent']) + int(q['d_affected'])} 평균 {q['avg_ms'] or '-'}ms")
            g = gaps.get(rid[6:])
            if g:
                verdict += f". 기준 초과: {g}"
            out.append(dict(id=rid, status="잼", verdict=clean(verdict), evidence=EVIDENCE, commit=COMMIT, rerun=RERUN))
    for gid, g in gaps.get("__rows__", {}).items():
        out.append(dict(id=gid, area="성능", item=clean(g["item"]), source=EVIDENCE, risk=g.get("risk", "중"), status=g["status"], verdict=clean(g["verdict"]),
                        evidence=g.get("evidence", EVIDENCE), commit=g.get("commit", COMMIT), rerun=g.get("rerun", RERUN)))
    with open(OUT, "w") as o:
        w = csv.DictWriter(o, COLS, delimiter="\t", quoting=csv.QUOTE_NONE, escapechar=None, lineterminator="\n")
        w.writeheader()
        for r in out:
            w.writerow({k: r.get(k, "") for k in COLS})
    print(f"행 {len(out)}, 다른 줄이 닫아 건너뛴 행 {len(skipped)}: {skipped}")


if __name__ == "__main__":
    sys.exit(main())
