#!/usr/bin/env python3
"""행사 오픈(사례 2·3), 외부 지연과 지도(사례 4), 지도 300 VU(사례 5)를 한 회 잰다.

사용: withlock.sh python3 run.py <APP_TAG> <실행 이름> [키=값 ...]
  tomcat=70 pool=10 disk=0 restart=1|0 script=openmap|mix warm=30s stub=단기,중기,천문연,OpenAI,S3HEAD(ms) k6_<이름>=<값>
  openmap은 k6_APPLICANTS(2000), k6_MAP_VUS(300), k6_MAP_DUR(150s), k6_OPEN_DELAY(45)를 받는다. k6_APPLICANTS=0이면 지도만 돈다.
  mix는 k6_VUS, k6_DURATION, k6_WEATHER_RATE, k6_HOT_RATE, k6_AI_RATE, k6_ATTACH_VUS를 받는다.
restart=0이면 떠 있는 앱을 그대로 이어서 잰다(이미지와 풀·스레드 값은 확인한다). 기본은 앱을 새로 띄우고 데운 뒤 잰다. 표본(sampler.py)과 후처리(analyze.py)까지 한다. 결과는 results/$LT_GROUP/runs/<이름>/result.json.
k6가 실패했거나 오픈인데 apply 201/409가 없으면 result.json을 지우고 1로 끝난다.
"""
import json, os, shutil, subprocess as sp, sys, time

import lt

STUB_IDS = {"vilage": "514", "midland": "515", "midta": "516", "riseset": "517", "openai": "5e4", "s3head": "5e3"}
FAST = "50,50,50,300,30"


def set_stub(delays):
    v, m, k, ai, head = (int(x) for x in delays.split(","))
    for name, d in (("vilage", v), ("midland", m), ("midta", m), ("riseset", k), ("openai", ai), ("s3head", head)):
        mapping = json.loads((lt.TOOLS / "stub/mappings" / f"{name}.json").read_text())
        if d > 0:
            mapping["response"]["fixedDelayMilliseconds"] = d
        r = sp.run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "-X", "PUT", "-H", "Content-Type: application/json", "--data-binary", json.dumps(mapping),
                    f"http://127.0.0.1:8089/__admin/mappings/5f1e0000-0000-4000-8000-000000000{STUB_IDS[name]}"], capture_output=True, text=True)
        if r.stdout != "200":
            raise SystemExit(f"스텁 {name} 지연 설정 실패: {r.stdout}")


def main():
    tag, name = sys.argv[1], sys.argv[2]
    o = dict(tomcat="70", pool="10", disk="0", script="openmap", warm="30s", stub=FAST, cpus="0,1")
    o.update(a.split("=", 1) for a in sys.argv[3:])
    out = lt.RESULTS / "runs" / name
    if (out / "result.json").exists():
        print("skip", name); return
    lt.require_lock()
    shutil.rmtree(out, ignore_errors=True)
    out = lt.run_dir(name)
    for c in ("moewadeul-lt-k6", "moewadeul-lt-sampler"):
        lt.sh("docker", "rm", "-f", c)
    disk = o["disk"] == "1"
    start = lt.reuse_app if o.get("restart") == "0" else lt.restart_app
    meta = start(tag, disk, LT_TOMCAT=o["tomcat"], LT_POOL=o["pool"])
    three = [lt.APP, lt.MYSQL, lt.CADDY]
    lt.sh("docker", "update", "--cpuset-cpus", o["cpus"], *three)
    (out / "spot-ids.json").write_text(json.dumps([int(x) for x in lt.sql("SELECT id FROM spot WHERE status='ACTIVE' ORDER BY RAND(1004) LIMIT 20000").split()]))
    script = f"/r/k/{o['script']}.js"
    if o["script"] == "mix":
        lt.sql("TRUNCATE ai_search_usage; DELETE FROM memory_image WHERE memory_id IS NULL;")
        set_stub(FAST)
        if o["warm"] != "0":
            sp.run(lt.k6(out, script, {"DURATION": "45s", "VUS": 50, "AI_RATE": 2, "ATTACH_VUS": 4}), capture_output=True)
        lt.sql("TRUNCATE ai_search_usage; DELETE FROM memory_image WHERE memory_id IS NULL;")
    elif o["warm"] != "0":
        sp.run(lt.k6(out, script, {"MAP_VUS": 20, "APPLICANTS": 0, "MAP_DUR": o["warm"]}), capture_output=True)
    set_stub(o["stub"])
    ids = {n: lt.sh("docker", "inspect", "-f", "{{.Id}}", c).stdout.strip() for n, c in (("app", lt.APP), ("mysql", lt.MYSQL), ("caddy", lt.CADDY))}
    sp.run(["docker", "run", "-d", "--rm", "--name", "moewadeul-lt-sampler", "--cgroupns=host", "--pid=host", "--network", lt.NET, "--cpuset-cpus", "4-7",
            "-v", "/sys/fs/cgroup:/h:ro", "-v", f"{out}:/out", "-v", f"{lt.TOOLS}:/s:ro", "python:3.12-alpine", "python", "-u", "/s/sampler.py"] + [f"{k}={v}" for k, v in ids.items()], capture_output=True)
    innodb = ("Innodb_deadlocks", "Innodb_row_lock_waits", "Innodb_row_lock_time", "Innodb_data_reads")
    meta.update({**o, "name": name, "start": time.time(), "innodb_before": lt.status(*innodb)})
    env = {k[3:]: v for k, v in o.items() if k.startswith("k6_")}
    k6 = sp.Popen(lt.k6(out, script, env, name="moewadeul-lt-k6", extra=["--system-tags=status,name", "--out", "csv=/out/k6.csv.gz", "--summary-export=/out/k6-summary.json"]),
                  stdout=open(out / "k6.log", "w"), stderr=sp.STDOUT)
    k6.wait()
    lt.sh("docker", "update", "--cpuset-cpus", "0,1", *three)
    meta["end"] = time.time(); meta["load_after"] = os.getloadavg()
    meta["innodb_after"] = lt.status(*innodb)
    meta["innodb_delta"] = {k: meta["innodb_after"][k] - meta["innodb_before"][k] for k in innodb}
    (out / "sampler.stop").touch(); time.sleep(3)
    lt.sh("docker", "rm", "-f", "moewadeul-lt-sampler")
    (out / "sampler.stop").unlink()
    lt.sh("docker", "cp", f"{lt.APP}:/tmp/gc.log", f"{out}/gc.log")
    meta["app_state"] = lt.sh("docker", "inspect", "-f", "{{.RestartCount}} {{.State.OOMKilled}} {{.State.Status}}", lt.APP).stdout.strip()
    (out / "app.log").write_text(lt.sh("docker", "logs", "--since", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(meta["start"] - 60)), lt.APP).stdout)
    (out / "meta.json").write_text(json.dumps(meta))
    print(sp.run(["python3", str(lt.TOOLS / "analyze.py"), str(out)], capture_output=True, text=True).stdout.strip())

    def fail(msg):
        (out / "result.json").unlink(missing_ok=True)
        print("FAIL", name, msg, file=sys.stderr); sys.exit(1)
    if k6.returncode != 0:
        fail(f"k6 종료 코드 {k6.returncode}")
    if not (out / "result.json").exists():
        fail("analyze.py가 result.json을 쓰지 못했다")
    if o["script"] == "openmap" and env.get("APPLICANTS", "2000") != "0":
        st = json.loads((out / "result.json").read_text()).get("apply", {}).get("status", {})
        if not any(k in ("201", "409") and v > 0 for k, v in st.items()):
            fail(f"apply 응답에 201/409가 없다 {st}")


if __name__ == "__main__":
    main()
