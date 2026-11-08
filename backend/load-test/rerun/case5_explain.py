#!/usr/bin/env python3
"""사례 5 마커 조회 힌트를 지금 MySQL 버전에서 다시 본다. 영역 넷의 지도 요청을 보내 앱이 실제로 보낸 SQL을 general log로 잡고,
힌트를 둔 문장과 뺀 문장을 EXPLAIN ANALYZE로 비교한다. 사용: withlock.sh python3 case5_explain.py <APP_TAG>
"""
import json, re, sys, time, urllib.request

import lt

AREAS = {"동네 0.02도": 0.02, "구 0.06도": 0.06, "시 0.15도": 0.15, "넓은 시 0.4도": 0.4}
CENTER = (37.55, 126.98)
HINT = "/*+ SET_VAR(optimizer_switch = 'prefer_ordering_index=off') */"


def main():
    lt.require_lock()
    meta = lt.restart_app(sys.argv[1], False)
    out = lt.run_dir("2026-11-08-rerun", "case5-explain")
    res = {"meta": meta, "mysql": lt.sql("SELECT @@version"), "areas": []}
    for label, w in AREAS.items():
        lt.sql("TRUNCATE mysql.general_log; SET GLOBAL log_output='TABLE'; SET GLOBAL general_log=1;")
        q = f"swLat={CENTER[0]-w/2:.5f}&swLng={CENTER[1]-w/2:.5f}&neLat={CENTER[0]+w/2:.5f}&neLng={CENTER[1]+w/2:.5f}&zoom=13"
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
