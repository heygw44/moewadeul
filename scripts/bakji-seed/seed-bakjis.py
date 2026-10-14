"""운영자가 CSV의 박지 목록을 박지 제보 API로 한 번에 등록한다.

처음 들어온 사람이 빈 지도를 보지 않도록 운영자가 널리 알려진 박지를 미리 넣을 때 쓴다. 서버는 화면에서 제보할 때와 같은 규칙으로
박지를 저장한다. 그래서 공원 경계 안 좌표에는 서버가 경고를 붙이고, 그 박지로는 베이스캠프를 열 수 없다.

CSV 열: name,lat,lng,description,hasWater,hasToilet,signalLevel,groundType
  - hasWater, hasToilet: true 또는 false
  - description, signalLevel(NONE·WEAK·GOOD), groundType(SOIL·GRASS·GRAVEL·SAND·ROCK·DECK): 비워 두면 보내지 않는다

인증: 운영 화면에 등록에 쓸 회원으로 로그인한 뒤, 브라우저 개발자 도구에서 쿠키 두 개를 복사해 환경 변수로 넘긴다.
  MOEWADEUL_SESSION  쿠키 SESSION의 값
  MOEWADEUL_XSRF     쿠키 XSRF-TOKEN의 값
스크립트는 두 값을 출력하지 않는다.

사용:
  # 확인 실행(기본): 요청을 보내지 않고, 건너뛸 행과 보낼 행만 출력한다. 근처 장소 조회(GET)는 보낸다.
  python3 scripts/bakji-seed/seed-bakjis.py --csv bakji-seed.csv
  # 실제 등록
  python3 scripts/bakji-seed/seed-bakjis.py --csv bakji-seed.csv --apply

같은 CSV로 다시 돌려도 중복 등록하지 않는다. 등록한 행은 결과 파일(기본: CSV 옆 *.results.csv)에 남기고, 다음 실행에서
결과 파일에 있는 이름은 건너뛴다. 좌표 200m 안에 이미 장소(공공 야영장·휴양림·박지)가 있는 행도 건너뛴다.
"""

import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_BASE_URL = "https://moewadeul.duckdns.org"
NEARBY_RADIUS_KM = 0.2
SLEEP_SECONDS = 1.0
RESULT_FIELDS = ["name", "spotId", "warned", "areaName", "duplicateCandidates", "status", "detail"]


class AuthError(Exception):
    """세션이 만료됐거나 CSRF 토큰이 맞지 않을 때 멈추려고 쓴다."""


def parse_args():
    parser = argparse.ArgumentParser(description="CSV의 박지를 박지 제보 API로 등록한다.")
    parser.add_argument("--csv", required=True, help="등록할 박지 CSV")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help=f"서비스 주소 (기본 {DEFAULT_BASE_URL})")
    parser.add_argument("--results", help="결과 파일 (기본: CSV 옆 *.results.csv)")
    parser.add_argument("--apply", action="store_true", help="실제로 등록한다. 없으면 확인 실행이다")
    return parser.parse_args()


def read_rows(path):
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def read_done_names(path):
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8", newline="") as f:
        return {row["name"] for row in csv.DictReader(f) if row.get("status") == "CREATED"}


def append_result(path, result):
    new_file = not os.path.exists(path)
    with open(path, "a", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=RESULT_FIELDS)
        if new_file:
            writer.writeheader()
        writer.writerow(result)


def to_bool(value, column, name):
    text = (value or "").strip().lower()
    if text in ("true", "false"):
        return text == "true"
    raise ValueError(f"{name}: {column}는 true 또는 false여야 한다 (지금 값: {value!r})")


def to_request_body(row):
    name = row["name"].strip()
    body = {
        "name": name,
        "lat": float(row["lat"]),
        "lng": float(row["lng"]),
        "hasWater": to_bool(row.get("hasWater"), "hasWater", name),
        "hasToilet": to_bool(row.get("hasToilet"), "hasToilet", name),
    }
    for column in ("description", "signalLevel", "groundType"):
        value = (row.get(column) or "").strip()
        if value:
            body[column] = value
    return body


class Client:
    def __init__(self, base_url, session, xsrf):
        self.base_url = base_url.rstrip("/")
        self.session = session
        self.xsrf = xsrf

    def _headers(self, with_body):
        headers = {"Accept": "application/json"}
        cookies = []
        if self.session:
            cookies.append(f"SESSION={self.session}")
        if self.xsrf:
            cookies.append(f"XSRF-TOKEN={self.xsrf}")
            headers["X-XSRF-TOKEN"] = self.xsrf
        if cookies:
            headers["Cookie"] = "; ".join(cookies)
        if with_body:
            headers["Content-Type"] = "application/json"
        return headers

    def _send(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path, data=data, method=method, headers=self._headers(body is not None)
        )
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                return response.status, json.loads(response.read() or b"{}")
        except urllib.error.HTTPError as e:
            payload = e.read()
            try:
                parsed = json.loads(payload or b"{}")
            except json.JSONDecodeError:
                parsed = {}
            if e.code in (401, 403):
                # 서버 오류 본문에는 비밀값이 없지만, 쿠키가 든 요청 헤더는 출력하지 않는다.
                raise AuthError(f"{method} {path} → {e.code} {parsed.get('code', '')}") from None
            return e.code, parsed

    def nearby(self, lat, lng):
        query = urllib.parse.urlencode({"lat": lat, "lng": lng, "radiusKm": NEARBY_RADIUS_KM, "size": 5})
        status, body = self._send("GET", f"/api/spots/nearby?{query}")
        if status != 200:
            raise RuntimeError(f"근처 장소 조회 실패: {status} {body.get('code', '')}")
        return body.get("content", [])

    def report(self, body):
        return self._send("POST", "/api/bakjis", body)


def main():
    args = parse_args()
    session = os.environ.get("MOEWADEUL_SESSION", "")
    xsrf = os.environ.get("MOEWADEUL_XSRF", "")
    if args.apply and not (session and xsrf):
        sys.exit("--apply에는 환경 변수 MOEWADEUL_SESSION과 MOEWADEUL_XSRF가 필요하다.")

    results_path = args.results or os.path.splitext(args.csv)[0] + ".results.csv"
    done = read_done_names(results_path)
    client = Client(args.base_url, session, xsrf)
    rows = read_rows(args.csv)
    counts = {"created": 0, "skipped": 0, "failed": 0, "planned": 0}

    print(f"{'등록' if args.apply else '확인 실행'}: {len(rows)}행, 대상 {args.base_url}, 결과 {results_path}")
    for index, row in enumerate(rows, start=1):
        name = (row.get("name") or "").strip()
        prefix = f"[{index}/{len(rows)}] {name}"
        if name in done:
            print(f"{prefix}: 이미 등록함, 건너뜀")
            counts["skipped"] += 1
            continue
        try:
            body = to_request_body(row)
        except (KeyError, ValueError) as e:
            print(f"{prefix}: CSV 값 오류, 건너뜀 ({e})")
            counts["failed"] += 1
            continue

        try:
            near = client.nearby(body["lat"], body["lng"])
        except AuthError as e:
            sys.exit(f"{prefix}: 인증 오류로 멈춤 ({e}). 다시 로그인해 쿠키를 새로 넣는다.")
        if near:
            names = ", ".join(f"{s['name']}({s['type']}, {s['distanceKm']}km)" for s in near)
            print(f"{prefix}: {int(NEARBY_RADIUS_KM * 1000)}m 안에 장소가 있어 건너뜀 → {names}")
            counts["skipped"] += 1
            continue

        if not args.apply:
            print(f"{prefix}: 등록할 예정 ({body['lat']}, {body['lng']})")
            counts["planned"] += 1
            continue

        try:
            status, response = client.report(body)
        except AuthError as e:
            sys.exit(f"{prefix}: 인증 오류로 멈춤 ({e}). 다시 로그인해 쿠키를 새로 넣는다.")
        if status == 201:
            warning = response.get("parkWarning", {})
            duplicates = response.get("duplicateCandidates", [])
            append_result(
                results_path,
                {
                    "name": name,
                    "spotId": response.get("spotId"),
                    "warned": warning.get("warned"),
                    "areaName": warning.get("areaName", ""),
                    "duplicateCandidates": ";".join(str(d["spotId"]) for d in duplicates),
                    "status": "CREATED",
                    "detail": "",
                },
            )
            mark = f"경고({warning.get('areaName', '')})" if warning.get("warned") else "경고 없음"
            print(f"{prefix}: 등록함 spotId={response.get('spotId')} {mark}")
            counts["created"] += 1
        else:
            detail = response.get("code", "") + " " + json.dumps(response.get("fieldErrors", []), ensure_ascii=False)
            append_result(results_path, {"name": name, "status": f"FAILED_{status}", "detail": detail.strip()})
            print(f"{prefix}: 실패 {status} {detail}")
            counts["failed"] += 1
        time.sleep(SLEEP_SECONDS)

    print(
        f"끝: 등록 {counts['created']}, 등록 예정 {counts['planned']}, 건너뜀 {counts['skipped']}, 실패 {counts['failed']}"
    )


if __name__ == "__main__":
    main()
