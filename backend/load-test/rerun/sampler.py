#!/usr/bin/env python3
# 사용: sampler.py 이름=컨테이너ID ... 앱 cgroup과 /proc를 1초마다, 앱 관리 포트(8081) 지표를 2초마다 /out에 적는다. run.py가 띄운다.
import json, os, re, sys, threading, time, urllib.request
ids = dict(a.split("=") for a in sys.argv[1:])
stop = "/out/sampler.stop"
KEEP = re.compile(r'^(tomcat_|hikaricp_connections(_active|_pending|_idle|_max|_acquire_seconds_(sum|count|max)|_usage_seconds_(sum|count)|_timeout_total)?[{ ]|jvm_memory_used_bytes|jvm_memory_committed_bytes|jvm_memory_max_bytes|jvm_gc_pause_seconds_(sum|count)|jvm_threads_(live|states)|jvm_buffer_memory|process_files_|process_cpu|http_server_requests_seconds_(bucket|sum|count)\{.*uri="(/api/spots|/api/programs/\{programId\}/applications)"|cache_|moewadeul_external_api_(requests_seconds_(sum|count)|rejected|fallbacks|skipped)|moewadeul_probe|executor_)')
def read(p):
    try:
        with open(p) as f: return f.read()
    except OSError: return ""
def kv(s):
    d = {}
    for l in s.splitlines():
        p = l.split()
        if len(p) == 2 and p[1].isdigit(): d[p[0]] = int(p[1])
    return d
def tcp(pid):
    st, lq = {}, None
    for fn in ("tcp", "tcp6"):
        for l in read(f"/proc/{pid}/net/{fn}").splitlines()[1:]:
            p = l.split()
            s = p[3]; port = int(p[1].rsplit(":", 1)[1], 16)
            st[s] = st.get(s, 0) + 1
            if s == "0A" and port == 8080:
                lq = int(p[4].split(":")[1], 16)
    nt = read(f"/proc/{pid}/net/netstat").splitlines(); lo = ld = None
    for i in range(0, len(nt) - 1, 2):
        if nt[i].startswith("TcpExt:"):
            d = dict(zip(nt[i].split(), nt[i + 1].split())); lo = int(d["ListenOverflows"]); ld = int(d["ListenDrops"])
    return {"lo": lo, "ld": ld, "listen_q": lq, "est": st.get("01", 0), "tw": st.get("06", 0), "synrcv": st.get("03", 0), "cw": st.get("08", 0)}
def scrape():
    with open("/out/prom.txt", "a") as out:
        while not os.path.exists(stop):
            t0 = time.time(); txt = ""; ok = 1
            try:
                txt = urllib.request.urlopen("http://app:8081/actuator/prometheus", timeout=30).read().decode()
            except Exception:
                ok = 0
            out.write(f"#T {t0:.3f} {time.time()-t0:.3f} {ok}\n")
            out.write("\n".join(l for l in txt.splitlines() if KEEP.match(l)) + "\n"); out.flush()
            time.sleep(max(0, 2 - (time.time() - t0)))
threading.Thread(target=scrape, daemon=True).start()
with open("/out/sys.jsonl", "a") as out:
    while not os.path.exists(stop):
        t0 = time.time(); r = {"t": round(t0, 3)}
        for n, i in ids.items():
            b = f"/h/docker/{i}"
            c = kv(read(b + "/cpu.stat")); m = kv(read(b + "/memory.stat")); e = kv(read(b + "/memory.events"))
            r[n] = {"cpu_us": c.get("usage_usec"), "cur": int(read(b + "/memory.current") or 0), "peak": int(read(b + "/memory.peak") or 0), "anon": m.get("anon"), "file": m.get("file"), "max": e.get("max"), "oom": e.get("oom_kill")}
        pid = (read(f"/h/docker/{ids['app']}/cgroup.procs").split() or [None])[0]
        if pid:
            s = read(f"/proc/{pid}/status")
            th = re.search(r"Threads:\s+(\d+)", s); rss = re.search(r"VmRSS:\s+(\d+)", s)
            try: fd = len(os.listdir(f"/proc/{pid}/fd"))
            except OSError: fd = None
            r["jvm"] = {"pid": pid, "fd": fd, "th": int(th.group(1)) if th else None, "rss_kb": int(rss.group(1)) if rss else None, **tcp(pid)}
        out.write(json.dumps(r) + "\n"); out.flush()
        time.sleep(max(0, 1 - (time.time() - t0)))
