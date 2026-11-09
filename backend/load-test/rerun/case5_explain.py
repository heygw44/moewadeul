#!/usr/bin/env python3
"""사례 5 마커 조회 힌트를 지금 MySQL 버전에서 다시 본다. 여러 영역의 지도 요청을 보내 앱이 실제로 보낸 SQL을 general log로 잡고,
힌트를 둔 문장과 뺀 문장을 EXPLAIN ANALYZE로 비교한다. 사용: withlock.sh python3 case5_explain.py <APP_TAG>
"""
import json, re, sys, time, urllib.request

import lt

# 마커 조회는 영역 안 장소가 상한 아래일 때만 돈다. 빽빽한 서울은 좁은 영역만, 장소가 드문 곳은 넓은 영역까지 마커가 된다.
AREAS = [("서울 0.01도", (37.55, 126.98), 0.01), ("서울 0.02도", (37.55, 126.98), 0.02), ("강원 산간 0.05도", (37.75, 128.6), 0.05), ("제주 0.05도", (33.36, 126.53), 0.05),
         ("강원 산간 0.15도", (37.75, 128.6), 0.15), ("서해 0.3도", (36.6, 125.9), 0.3)]
HINT = "/*+ SET_VAR(optimizer_switch = 'prefer_ordering_index=off') */"


def main():
    lt.require_lock()
    meta = lt.restart_app(sys.argv[1], False)
    out = lt.run_dir("case5-explain")
    res = {"meta": meta, "mysql": lt.sql("SELECT @@version"), "areas": []}
    for label, center, w in AREAS:
        lt.sql("TRUNCATE mysql.general_log; SET GLOBAL log_output='TABLE'; SET GLOBAL general_log=1;")
        q = f"swLat={center[0]-w/2:.5f}&swLng={center[1]-w/2:.5f}&neLat={center[0]+w/2:.5f}&neLng={center[1]+w/2:.5f}&zoom=13"
        body = json.loads(urllib.request.urlopen(f"{lt.BASE}/api/spots?{q}", timeout=60).read())
        time.sleep(0.5)
        lt.sql("SET GLOBAL general_log=0;")
        stmts = [s for s in lt.sql("SELECT CONVERT(argument USING utf8mb4) FROM mysql.general_log WHERE command_type IN ('Query','Execute') AND argument LIKE '%prefer_ordering_index%'").splitlines()]
        row = {"area": label, "markers": len(body.get("markers") or []), "clusters": len(body.get("clusters") or [])}
        if stmts:
            sql_text = stmts[-1].replace("\\n", " ").replace("\\t", " ")
            for name, text in (("hint", sql_text), ("no_hint", sql_text.replace(HINT, ""))):
                plan = lt.sql("EXPLAIN ANALYZE " + text).replace("\\n", "\n")
                idx = sorted(set(re.findall(r"using (\w+)", plan)))
                top = re.search(r"actual time=[\d.]+\.\.([\d.]+)", plan)
                row[name] = {"indexes": idx, "ms": float(top.group(1)) if top else None, "plan": plan[:1500]}
        res["areas"].append(row)
        print(json.dumps({k: (v if not isinstance(v, dict) else {kk: vv for kk, vv in v.items() if kk != "plan"}) for k, v in row.items()}, ensure_ascii=False), flush=True)
    lt.sql("TRUNCATE mysql.general_log; SET GLOBAL log_output='FILE';")
    (out / "result.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
