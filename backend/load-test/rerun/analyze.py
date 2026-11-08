#!/usr/bin/env python3
# 사용: analyze.py <실행 폴더>. k6 CSV, GC 로그, 표본을 오픈 구간으로 잘라 result.json과 한 줄 요약을 낸다.
import csv, datetime, gzip, json, os, re, sys
D = sys.argv[1]; meta = json.load(open(f"{D}/meta.json"))
def pct(v, p):
    if not v: return None
    v = sorted(v); return round(v[min(len(v) - 1, int(p * len(v)))], 1)
def stats(v): return {"n": len(v), "p50": pct(v, .5), "p95": pct(v, .95), "p99": pct(v, .99), "max": round(max(v), 1) if v else None}
reqs = {}; status = {}; counters = {}
with gzip.open(f"{D}/k6.csv.gz", "rt") as f:
    r = csv.DictReader(f)
    for x in r:
        if x["metric_name"] in ("weather_empty", "ai_ok", "ai_503", "ai_429", "ai_other", "attach_ok", "attach_503", "attach_429", "attach_other", "issue_fail"):
            counters[x["metric_name"]] = counters.get(x["metric_name"], 0) + float(x["metric_value"]); continue
        if x["metric_name"] != "http_req_duration": continue
        n = x["name"]; 
        if n in ("login", "csrf", "clock", "program-create"): continue
        end = int(x["timestamp"]) / 1000; d = float(x["metric_value"])
        reqs.setdefault(n, []).append((end - d / 1000, end, d, x["status"]))
apply = reqs.get("program-apply", []); mp = reqs.get("spots-area", [])
others = {}
for n, v in reqs.items():
    if n in ("spots-area", "program-apply"): continue
    c = {}
    for x in v: c[x[3]] = c.get(x[3], 0) + 1
    others[n] = {**{k: round(z, 1) if z is not None else None for k, z in stats([x[2] for x in v]).items()}, "status": c}
res = {"name": meta["name"], "cfg": {k: meta.get(k) for k in ("tag", "tomcat", "pool", "image_sha", "disk_limit", "stub", "script")}, "app_state": meta.get("app_state"), "load": [meta["load_before"][0], meta["load_after"][0]],
       "innodb_delta": meta.get("innodb_delta"), "counters": counters}
if apply:
    a0 = min(x[0] for x in apply); a1 = max(x[1] for x in apply)
    c = {}
    for x in apply: c[x[3]] = c.get(x[3], 0) + 1
    res["apply"] = {**stats([x[2] for x in apply]), "status": c, "window_s": round(a1 - a0, 1)}
    # 초기에 지도 몰림을 빼고 오픈 전 30초만 평소 값으로 본다.
    res["map_pre"] = stats([x[2] for x in mp if a0 - 30 <= x[0] < a0])
    res["map_open"] = stats([x[2] for x in mp if a0 <= x[0] <= a1])
    res["map_post"] = stats([x[2] for x in mp if a1 + 15 <= x[0]])
    res["map_err_open"] = sum(1 for x in mp if a0 <= x[0] <= a1 and not x[3].startswith("2"))
    W = (a0, a1)
else:
    W = (meta["start"] + 45, meta["end"]); res["map_steady"] = stats([x[2] for x in mp if W[0] <= x[0]])
    res["map_err"] = sum(1 for x in mp if not x[3].startswith("2"))
res["map_err_total"] = sum(1 for x in mp if not x[3].startswith("2"))
res["others"] = others
res["window"] = [round(W[0], 1), round(W[1], 1)]
# GC
gc = []; sp_tot = 0; sp_n = 0; last_full_after = None; heap_max = None; phases = {}
pat = re.compile(r"^\[([^\]]+)\]\[([\d.]+)s\]\[\w+\s*\]\[([^\]]+)\] (.*)")
pause = re.compile(r"GC\(\d+\) Pause (\w+).*? (\d+)M->(\d+)M\((\d+)M\) ([\d.]+)ms")
for l in open(f"{D}/gc.log", errors="replace") if os.path.exists(f"{D}/gc.log") else []:
    m = pat.match(l)
    if not m: continue
    ts = datetime.datetime.strptime(m[1], "%Y-%m-%dT%H:%M:%S.%f%z").timestamp(); tags = m[3].strip(); msg = m[4]
    if tags == "gc":
        p = pause.search(msg)
        if p: gc.append((ts, p[1], int(p[2]), int(p[3]), int(p[4]), float(p[5])))
    elif tags == "safepoint" and W[0] <= ts <= W[1]:
        p = re.search(r"Total: (\d+) ns", msg)
        if p: sp_tot += int(p[1]); sp_n += 1
w = max(W[1] - W[0], .001); inw = [g for g in gc if W[0] <= g[0] <= W[1]]
pmax = max([g[5] for g in inw], default=0); psum = sum(g[5] for g in inw)
gs = gc and [g for g in gc if g[0] >= W[0] - 60 and g[0] <= W[1] + 60] or []
res["gc"] = {"algo": next((re.search(r"Using (\w+)", l)[1] for l in open(f"{D}/gc.log", errors="replace") if "Using " in l), None) if os.path.exists(f"{D}/gc.log") else None,
    "window_s": round(w, 1), "n": len(inw), "full": sum(1 for g in inw if g[1] == "Full"), "pause_sum_ms": round(psum, 1), "share_pct": round(psum / w / 10, 2), "pause_max_ms": pmax,
    "safepoint_ms": round(sp_tot / 1e6, 1), "heap_after_max": max([g[3] for g in inw], default=None), "heap_before_max": max([g[2] for g in inw], default=None), "heap_cap": max([g[4] for g in inw], default=None),
    "run_all_n": len(gc), "run_full": sum(1 for g in gc if g[1] == "Full")}
# 지표
tag = re.compile(r'^([a-zA-Z_:0-9]+)(?:\{(.*)\})? ([-+0-9.eE]+|NaN)')
samples = []; cur = None
for l in open(f"{D}/prom.txt", errors="replace") if os.path.exists(f"{D}/prom.txt") else []:
    if l.startswith("#T"):
        p = l.split(); cur = {"t": float(p[1]), "lat": float(p[2]), "ok": int(p[3]), "m": {}}; samples.append(cur); continue
    m = tag.match(l)
    if m and cur is not None:
        cur["m"][(m[1], m[2] or "")] = float(m[3]) if m[3] != "NaN" else 0.0
def val(s, name, sub=""): return sum(v for (n, t), v in s["m"].items() if n == name and sub in t)
ws = [s for s in samples if W[0] - 2 <= s["t"] <= W[1] + 2 and s["ok"]]
if ws:
    busy = [val(s, "tomcat_threads_busy_threads", "8080") for s in ws]; act = [val(s, "hikaricp_connections_active") for s in ws]; pend = [val(s, "hikaricp_connections_pending") for s in ws]
    conn = [val(s, "tomcat_connections_current_connections", "8080") for s in ws]; files = [val(s, "process_files_open_files") for s in ws]
    mx = float(meta.get("tomcat")); pl = float(meta.get("pool")); a, b = ws[0], ws[-1]
    q = lambda n, sub="": val(b, n, sub) - val(a, n, sub)
    acn = q("hikaricp_connections_acquire_seconds_count")
    # 서버 안쪽 지도 시간. 스레드를 얻은 뒤부터 잰 값이라 k6 값과의 차이가 대기열 몫이다.
    uri = 'uri="/api/spots"'; cnt = q("http_server_requests_seconds_count", uri); tot = q("http_server_requests_seconds_sum", uri)
    bk = {}
    for (n, t), v in b["m"].items():
        if n == "http_server_requests_seconds_bucket" and uri in t:
            le = re.search(r'le="([^"]+)"', t)[1]; bk[le] = bk.get(le, 0) + v - a["m"].get((n, t), 0)
    res["srv"] = {"tomcat_busy_max": max(busy), "tomcat_busy_full_pct": round(100 * sum(1 for x in busy if x >= mx) / len(busy)), "hikari_active_max": max(act), "hikari_full_pct": round(100 * sum(1 for x in act if x >= pl) / len(act)), "hikari_pending_max": max(pend),
        "acquire_avg_ms": round(q("hikaricp_connections_acquire_seconds_sum") / acn * 1000, 1) if acn else None, "usage_avg_ms": round(q("hikaricp_connections_usage_seconds_sum") / max(q("hikaricp_connections_usage_seconds_count"), 1) * 1000, 1),
        "conn_max": max(conn), "files_max": max(files), "scrape_n": len(ws), "scrape_lat_max": max(s["lat"] for s in ws), "map_srv_avg_ms": round(tot / cnt * 1000, 1) if cnt else None, "map_srv_n": cnt, "map_srv_buckets": {k: round(v) for k, v in sorted(bk.items(), key=lambda kv: float(kv[0]) if kv[0] != "+Inf" else 9e9)}}
    jm = {}
    for s in samples[-1:]:
        for (n, t), v in s["m"].items():
            if n == "jvm_memory_used_bytes": jm[re.search(r'id="([^"]+)"', t)[1]] = round(v / 1048576, 1)
    res["jvm_mem_end_mib"] = jm
# 커넥션 경보(deploy/grafana/alert-rules.json): max(hikaricp_connections_acquire_seconds_max) > 3을 60초마다 평가한다.
# 표본은 2초 간격이라 3초를 넘은 구간이 60초 이상 이어졌으면 어느 평가 시각에도 걸린다(울림), 1~59초면 평가 시각에 따라 다르다(아마).
acq = [(s["t"], val(s, "hikaricp_connections_acquire_seconds_max")) for s in samples if s["ok"]]
run, best = None, 0.0
for t, v in acq:
    if v > 3:
        run = run if run is not None else t; best = max(best, t - run + 2)
    else:
        run = None
res["alert"] = {"acquire_max_s": round(max((v for _, v in acq), default=0), 2), "over3_longest_s": round(best, 1), "fires": "yes" if best >= 60 else ("maybe" if best > 0 else "no")}
# cgroup
sys_ = [json.loads(l) for l in open(f"{D}/sys.jsonl")] if os.path.exists(f"{D}/sys.jsonl") else []
ww = [s for s in sys_ if W[0] <= s["t"] <= W[1]]
if len(ww) >= 2:
    cpu = {n: round((ww[-1][n]["cpu_us"] - ww[0][n]["cpu_us"]) / 1e6 / (ww[-1]["t"] - ww[0]["t"]), 2) for n in ("app", "mysql", "caddy")}
    res["cpu_cores"] = {**cpu, "sum": round(sum(cpu.values()), 2)}
    res["mem"] = {n: {"peak_mib": round(sys_[-1][n]["peak"] / 1048576), "anon_max_mib": round(max(s[n]["anon"] or 0 for s in sys_) / 1048576), "cur_max_mib": round(max(s[n]["cur"] for s in sys_) / 1048576), "max_events": sys_[-1][n]["max"], "oom": sys_[-1][n]["oom"]} for n in ("app", "mysql", "caddy")}
    jv = [s["jvm"] for s in sys_ if "jvm" in s]
    res["jvm"] = {"fd_max": max(x["fd"] or 0 for x in jv), "th_max": max(x["th"] or 0 for x in jv), "rss_max_mib": round(max(x["rss_kb"] or 0 for x in jv) / 1024), "listen_q_max": max(x["listen_q"] or 0 for x in jv), "est_max": max(x["est"] for x in jv), "listen_overflow": (jv[-1].get("lo") or 0) - (jv[0].get("lo") or 0), "listen_drops": (jv[-1].get("ld") or 0) - (jv[0].get("ld") or 0)}
json.dump(res, open(f"{D}/result.json", "w"), ensure_ascii=False, indent=1)
fmt = lambda s: f"{s['p50']}/{s['p95']}/{s['max']}" if s and s.get("n") else "-"
g = res["gc"]; sv = res.get("srv", {})
print(res["name"], "disk", res["cfg"]["disk_limit"], "state", res["app_state"], "innodb", res.get("innodb_delta"), "alert", res["alert"], "counters", counters, "apply", fmt(res.get("apply")), res.get("apply", {}).get("status"), "win", res.get("apply", {}).get("window_s"), "| map pre", fmt(res.get("map_pre")), "open", fmt(res.get("map_open")), "post", fmt(res.get("map_post")), "steady", fmt(res.get("map_steady")), "others", {k: [v["p95"], v["max"], v["status"]] for k, v in others.items()}, "err", res["map_err_total"], "| gc", g["n"], "full", g["full"], "sum", g["pause_sum_ms"], "share", g["share_pct"], "max", g["pause_max_ms"], "heap", g["heap_before_max"], g["heap_after_max"], g["heap_cap"], "| busy", sv.get("tomcat_busy_max"), sv.get("tomcat_busy_full_pct"), "hik", sv.get("hikari_active_max"), sv.get("hikari_full_pct"), "pend", sv.get("hikari_pending_max"), "acq", sv.get("acquire_avg_ms"), "use", sv.get("usage_avg_ms"), "srvmap", sv.get("map_srv_avg_ms"), "conn", sv.get("conn_max"), "| cpu", res.get("cpu_cores"), "| peak", res.get("mem", {}).get("app", {}).get("peak_mib"), "anon", res.get("mem", {}).get("app", {}).get("anon_max_mib"), "maxev", res.get("mem", {}).get("app", {}).get("max_events"), "fd", res.get("jvm", {}).get("fd_max"), "lq", res.get("jvm", {}).get("listen_q_max"), "lo", res.get("jvm", {}).get("listen_overflow"), "scrapelat", sv.get("scrape_lat_max"))
