#!/usr/bin/env python3
"""results/2026-11-08-rerun/runs/의 결과를 사례별 마크다운 표로 찍는다. 사용: python3 summary.py > 표.md"""
import glob, json, os, re, statistics

import lt

RUNS = lt.MAIN / "backend/load-test/results/2026-11-08-rerun/runs"


def load(pattern, file="result.json"):
    out = []
    for d in sorted(glob.glob(str(RUNS / pattern))):
        p = os.path.join(d, file)
        if os.path.exists(p):
            out.append((os.path.basename(d), json.load(open(p))))
    return out


def rng(xs, fmt="{:.0f}"):
    xs = [x for x in xs if x is not None]
    if not xs:
        return "-"
    m = statistics.median(xs)
    return fmt.format(m) + (f" ({fmt.format(min(xs))}~{fmt.format(max(xs))})" if len(xs) > 1 else "")


def table(head, rows):
    print("| " + " | ".join(head) + " |")
    print("|" + "---|" * len(head))
    for r in rows:
        print("| " + " | ".join(str(x) for x in r) + " |")
    print()


def case1():
    path = RUNS / "case1/result.jsonl"
    if not path.exists():
        return
    rows = [json.loads(l) for l in open(path)]
    print("## 사례 1\n")
    sql = [r for r in rows if r["kind"] == "sql-no-recent-ip"]
    out = []
    for st in ("none", "ip", "both"):
        for rnd in sorted({r["round"] for r in sql}):
            s = [r for r in sql if r["state"] == st and r["round"] == rnd]
            out.append([st, rnd, " / ".join(f"{r['ms']:.1f}" for r in s), " / ".join(str(r["data_reads"]) for r in s), s[0]["handler_sum"], s[-1]["lru_after"]["pages"]])
    table(["인덱스", "묶음", "SQL ms(연속 4회)", "디스크 읽기", "Handler 읽기", "버퍼 풀의 표 쪽 수"], out)
    api = {}
    for r in rows:
        if r["kind"].startswith("api-"):
            st = "both" if len(r["indexes"]) == 2 else ("ip" if r["indexes"] else "none")
            api.setdefault((r["kind"], st), []).append(r)
    out = []
    for (k, st), rs in sorted(api.items()):
        codes = {}
        for r in rs:
            for c in r["status"]:
                codes[c[0]] = codes.get(c[0], 0) + 1
        out.append([k, st, len(rs), rng([r["max"] * 1000 for r in rs]), codes, sum(r["busy_log_lines"] for r in rs)])
    table(["재발송", "인덱스", "회", "가장 늦은 응답 ms 중앙값(범위)", "응답 코드", "잠금 대기 로그 줄"], out)
    out = [[r["order"], "있음" if len(r["indexes"]) == 2 else "없음", r["deleted"], r["sec"], r["batches"], r["rows_read"], r["data_reads"]] for r in rows if r["kind"] == "cleanup"]
    table(["시드 모양", "V69 created_at 인덱스", "지운 행", "초", "1,000건 묶음", "읽은 행", "디스크 읽기"], out)


def case2():
    print("## 사례 2\n")
    heads = load("c2-head-*", "summary.json")
    table(["회차", "신고 응답", "신고 최소~최대 초", "지도 요청", "지도 실패", "지도 최대 ms", "지도 최장 빈틈 초", "풀 대기 최대", "획득 최댓값 초"],
          [[n, s["report_status"], f"{s['report_min']:.2f}~{s['report_max']:.2f}", s["map_n"], s["map_fail"], round(s["map_max"]), round(s["map_max_gap_s"], 2), s["pending_max"], round(s["acquire_max"], 2)] for n, s in heads])
    cells = {}
    for n, s in load("c2-p*", "summary.json"):
        m = re.match(r"c2-p(\d+)-r(\d+)-\d+", n)
        cells.setdefault((int(m[1]), int(m[2])), []).append(s)
    table(["풀", "신고", "회", "지도 최대 ms 중앙값(범위)", "지도 실패 합", "신고 응답", "MySQL 연결 최대"],
          [[p, r, len(v), rng([s["map_max"] for s in v]), sum(s["map_fail"] for s in v), v[0]["report_status"], max(s["threads_connected_max"] for s in v)] for (p, r), v in sorted(cells.items())])


def case3():
    print("## 사례 3\n")
    out = []
    for n, r in load("c3-*"):
        e = r["endpoints"]
        out.append([n, r["seat_index"], r["innodb_delta"]["Innodb_deadlocks"], r["innodb_delta"]["Innodb_row_lock_waits"], r["innodb_delta"].get("Innodb_lock_timeouts"),
                    f"{r['seat_holders']}/{r['capacity']}", e.get("apply", {}).get("busy_503"), e.get("cancel", {}).get("codes"), round(e.get("pay", {}).get("p95") or 0), round(e.get("cancel", {}).get("p95") or 0)])
    table(["회차", "자리 세기 인덱스", "데드락", "행 잠금 대기", "잠금 대기 초과", "자리/정원", "신청 503", "취소 응답", "결제 p95 ms", "취소 p95 ms"], out)
    rounds = sorted(glob.glob(str(RUNS / "rounds/*.json")))
    if rounds:
        table(["경합", "라운드", "자리 세기 인덱스", "결과"], [[d["scenario"], d["rounds"], d.get("seatCountIndex", "-"), d["counts"]] for d in (json.load(open(p)) for p in rounds)])
    open_rows(["open-*", "openmap-*"])


def open_rows(patterns):
    out = []
    for pat in patterns:
        for n, r in load(pat):
            a = r.get("apply", {}); sv = r.get("srv", {})
            out.append([n, r["cfg"]["pool"], r["cfg"]["tomcat"], a.get("status"), a.get("p95"), (r.get("map_open") or {}).get("p95"), (r.get("map_pre") or {}).get("p95"),
                        sv.get("hikari_pending_max"), r["alert"]["acquire_max_s"], r["alert"]["fires"], r["innodb_delta"]["Innodb_deadlocks"], [round(x, 1) for x in r["load"]]])
    table(["회차", "풀", "Tomcat", "신청 응답", "신청 p95 ms", "오픈 중 지도 p95 ms", "오픈 전 지도 p95 ms", "풀 대기 최대", "획득 최댓값 초", "경보(>3초 60초 평가)", "데드락", "load 전/후"], out)


def case4():
    print("## 사례 4\n")
    out = []
    for n, r in load("c4-*"):
        o = r.get("others", {}); sv = r.get("srv", {}); w = o.get("spot-weather", {})
        c = r.get("counters", {})
        wn = w.get("n") or 0
        out.append([n, r["cfg"]["tomcat"], (r.get("map_steady") or {}).get("p95"), (r.get("map_steady") or {}).get("max"), w.get("p95"), w.get("status"),
                    f"{100 * c.get('weather_empty', 0) / wn:.1f}%" if wn else "-", sv.get("tomcat_busy_max"), sv.get("hikari_pending_max"),
                    r.get("mem", {}).get("app", {}).get("anon_max_mib"), r.get("cpu_cores", {}).get("app"), {k: int(v) for k, v in c.items() if k.startswith("attach")}, [round(x, 1) for x in r["load"]]])
    table(["회차", "Tomcat", "지도 p95 ms", "지도 최대 ms", "날씨 p95 ms", "날씨 응답", "날씨 빈 응답", "바쁜 스레드 최대", "풀 대기 최대", "앱 anon 최대 MiB", "앱 CPU 코어", "첨부", "load 전/후"], out)


def case5():
    print("## 사례 5\n")
    out = [[n, (r.get("map_steady") or {}).get("p50"), (r.get("map_steady") or {}).get("p95"), (r.get("map_steady") or {}).get("max"), r.get("map_err_total"), r.get("cpu_cores"), [round(x, 1) for x in r["load"]]] for n, r in load("c5-*")]
    table(["회차", "지도 p50 ms", "지도 p95 ms", "지도 최대 ms", "실패", "CPU 코어", "load 전/후"], out)
    p = RUNS / "case5-explain/result.json"
    if p.exists():
        d = json.load(open(p))
        table(["영역", "마커", "묶음", "힌트 인덱스", "힌트 ms", "힌트 없이 인덱스", "힌트 없이 ms"],
              [[a["area"], a["markers"], a["clusters"], a.get("hint", {}).get("indexes"), a.get("hint", {}).get("ms"), a.get("no_hint", {}).get("indexes"), a.get("no_hint", {}).get("ms")] for a in d["areas"]])


if __name__ == "__main__":
    for f in (case1, case2, case3, case4, case5):
        f()
