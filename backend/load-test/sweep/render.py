#!/usr/bin/env python3
"""돌지 않은 매퍼 쿼리의 SQL을 XML에서 만든다. if는 모두 켜고, choose는 첫 갈래를 쓰고, foreach는 값 두 개로 편다.

  render.py    out/rendered.json을 만든다. 값을 찾지 못한 파라미터를 알린다.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import glob
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

OUT = D + "/out"
MAPPERS = ROOT + "/backend/src/main/resources/mapper"

import paramvalues  # noqa: E402


def load():
    frags, stmts = {}, {}
    for f in glob.glob(MAPPERS + "/**/*.xml", recursive=True):
        root = ET.parse(f).getroot()
        ns = root.attrib["namespace"]
        short = ns.split(".")[-1]
        for el in root:
            if el.tag == "sql":
                frags[f"{ns}.{el.attrib['id']}"] = el
                frags.setdefault(el.attrib["id"] + "@" + ns, el)
            elif el.tag in ("select", "insert", "update", "delete"):
                stmts[f"{short}.{el.attrib['id']}"] = (ns, el)
    return frags, stmts


def strip_prefix(s, words):
    t = s.strip()
    for w in words:
        if t.upper().startswith(w):
            return t[len(w):]
    return t


def sub_props(s, props):
    return re.sub(r"\$\{(\w+)\}", lambda m: props.get(m.group(1), m.group(0)), s)


def fill(sql, qid, vals, missing):
    def repl(m):
        name = m.group(1).split(",")[0].strip()
        v = paramvalues.value(qid, name, vals)
        if v is None:
            missing.setdefault(qid, []).append(name)
            return "NULL"
        return v
    return re.sub(r"#\{([^}]+)\}", repl, sql)


def main():
    frags, stmts = load()
    vals = paramvalues.base_values()
    only = sys.argv[1:] if len(sys.argv) > 1 else None
    res, missing = {}, {}
    for qid, (ns, el) in stmts.items():
        if only and qid not in only:
            continue
        try:
            sql = render_stmt(el, ns, frags, vals, qid, missing)
        except Exception as e:  # noqa: BLE001
            print("렌더 실패", qid, repr(e))
            continue
        res[qid] = re.sub(r"\s+", " ", sql).strip()
    # JPQL은 XML에 없어서 같은 뜻의 SQL을 손으로 적는다. 로그에 실제 SQL이 남은 쿼리는 이것을 쓰지 않는다.
    res.update({
        "CommunityPostJpaRepository.findByIdForShare": f"SELECT * FROM community_post WHERE id = {vals['postId']} FOR SHARE",
        "SpotJpaRepository.findByIdForUpdate": f"SELECT * FROM spot WHERE id = {vals['spotId']} FOR UPDATE",
        "MemberJpaRepository.existsByNicknameAndIdNot": f"SELECT COUNT(*) > 0 FROM member WHERE nickname = 'bg0000001' AND id <> {vals['memberId']}",
    })
    json.dump(res, open(OUT + "/rendered.json", "w"), ensure_ascii=False, indent=0)
    print(f"렌더 {len(res)}개, 값 없음 {len(missing)}개")
    for k, v in missing.items():
        print(" ", k, sorted(set(v)))


def render_stmt(el, ns, frags, vals, qid, missing):
    # foreach 안의 #{item} 값을 넣으려고 렌더 중에 바로 채우지 않고, 먼저 표식을 남긴 뒤 채운다.
    def r(node, props, item):
        parts = [expand_text(node.text or "", item, props)]
        for c in node:
            parts.append(t2(c, props, item))
            parts.append(expand_text(c.tail or "", item, props))
        return "".join(parts)

    def expand_text(s, item, props):
        s = sub_props(s, props)
        s = re.sub(r"\$\{([^}]+)\}", lambda m: paramvalues.dollar(qid, m.group(1), props), s)
        if item:
            for k, v in item.items():
                s = re.sub(r"#\{" + re.escape(k) + r"([,.}][^}]*)?\}?", lambda m: paramvalues.item_value(qid, k, m.group(1) or "", v), s)
        return s

    def t2(c, props, item):
        t = c.tag
        if t == "include":
            ref = sub_props(c.attrib["refid"], props)
            full = ref if "." in ref else f"{ns}.{ref}"
            p2 = dict(props)
            for pr in c.findall("property"):
                p2[pr.attrib["name"]] = sub_props(pr.attrib["value"], props)
            return r(frags[full], p2, item)
        if t == "if":
            return "" if paramvalues.test_false(qid, c.attrib["test"]) else r(c, props, item)
        if t == "choose":
            for w in c.findall("when"):
                if not paramvalues.test_false(qid, w.attrib["test"]):
                    return r(w, props, item)
            o = c.find("otherwise")
            return r(o, props, item) if o is not None else ""
        if t == "where":
            s = r(c, props, item)
            return " WHERE " + strip_prefix(s, ["AND ", "OR "]) if s.strip() else ""
        if t == "set":
            return " SET " + r(c, props, item).strip().rstrip(",")
        if t == "trim":
            s = r(c, props, item).strip()
            for w in (c.attrib.get("prefixOverrides") or "").split("|"):
                if w and s.upper().startswith(w.upper()):
                    s = s[len(w):]
            for w in (c.attrib.get("suffixOverrides") or "").split("|"):
                if w and s.upper().endswith(w.upper()):
                    s = s[: -len(w)]
            return f" {c.attrib.get('prefix', '')} {s} {c.attrib.get('suffix', '')} "
        if t == "foreach":
            out = [r(c, props, dict(item or {}, **{c.attrib["item"]: it})) for it in paramvalues.collection(qid, c.attrib.get("collection"))]
            return c.attrib.get("open", "") + c.attrib.get("separator", ",").join(out) + c.attrib.get("close", "")
        if t in ("bind", "selectKey"):
            return ""
        raise ValueError(t)

    sql = r(el, {}, None)
    return fill(sql, qid, vals, missing)


if __name__ == "__main__":
    main()
