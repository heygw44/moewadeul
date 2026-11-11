"""장부의 API 118개를 어떻게 부를지 적는다. 경로 변수, 행위자, 본문은 시드에서 뽑는다.

rows는 고정 목록이거나 SQL이다. SQL의 {P:열}은 API마다 다른 나머지 구간으로 바뀌어서, 소비형 API끼리 같은 행을 쓰지 않는다.
actor 열이 있으면 그 회원으로 로그인한다. consume=True면 행 하나를 한 번만 쓴다(쓰기).
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from typing import Any, Callable, Optional

ADMIN = "admin@loadtest.invalid"
USER1 = "user000001@loadtest.invalid"
HEAVY = "bg0000001@loadtest.invalid"
# 시드 회원의 검증용 비밀번호(seed.sql 머리말). 저장소에 값을 두지 않으려고 환경 변수로 받는다.
PASSWORD = os.environ["LOADTEST_PASSWORD"]
NEW_PASSWORD = PASSWORD + "-2"

# 소비형 API가 쓰는 행 수: 혼자 재기 30 + 동시 200
WRITE_ROWS = 230
READ_ROWS = 200

MEMBER_ACTIVE = "m.status = 'ACTIVE'"


def iso(dt):
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def days(n):
    return (date.today() + timedelta(days=n)).isoformat()


def now():
    return datetime.now(timezone.utc)


@dataclass
class Api:
    key: str
    rows: Any = None
    actor: Optional[str] = None
    body: Optional[Callable] = None
    query: Optional[Callable] = None
    consume: bool = False
    idem: bool = False
    n: int = 0
    ok: Optional[list] = None
    note: str = ""
    # IP당 제한이 있는 API는 요청마다 IP를 달리해야 2xx를 받는다. Caddy는 X-Forwarded-For를 덮어쓰므로 앱으로 바로 보낸다.
    direct: bool = False
    # load는 소비형에서 동시에 보낼 건수를 정한다. 0이면 행이 남는 만큼 보낸다.
    pre: Optional[str] = None


REG = {}


def api(key, **kw):
    assert key not in REG, key
    REG[key] = Api(key=key, **kw)


# 조회 (공개)
BOXES = [
    # 시·군(8), 시·도(10), 동네(5), 전국(13). map-area.js와 같은 비율로 섞는다.
    dict(swLat=37.46, swLng=127.14, neLat=37.635, neLng=127.36, zoom=8),
    dict(swLat=37.20, swLng=126.81, neLat=37.90, neLng=127.69, zoom=10),
    dict(swLat=37.54, swLng=127.24, neLat=37.562, neLng=127.267, zoom=5),
    dict(swLat=33.5, swLng=124.3, neLat=39.1, neLng=131.3, zoom=13),
    dict(swLat=36.26, swLng=128.64, neLat=36.435, neLng=128.86, zoom=8),
    dict(swLat=35.0, swLng=127.81, neLat=35.7, neLng=128.69, zoom=10),
    dict(swLat=37.66, swLng=128.19, neLat=37.835, neLng=128.41, zoom=8),
    dict(swLat=37.74, swLng=128.28, neLat=37.762, neLng=128.307, zoom=5),
]
api("GET /api/spots", rows=[
    *BOXES,
    dict(BOXES[0], hideClosed="true"),
    dict(BOXES[1], hasWater="true"),
    dict(BOXES[0], types="BAKJI"),
    dict(BOXES[1], settings="MOUNTAIN", types="CAMPSITE"),
    dict(swLat=30.0, swLng=120.0, neLat=40.0, neLng=135.0, zoom=14),
], query=lambda r: r)
api("GET /api/spots/nearby", rows=[
    dict(lat=37.55, lng=127.25, radiusKm=5),
    dict(lat=37.55, lng=127.25, radiusKm=20),
    dict(lat=36.35, lng=128.75, radiusKm=50),
    dict(lat=35.35, lng=128.25, radiusKm=10, hasWater="true"),
    dict(lat=37.75, lng=128.30, radiusKm=20, hideClosed="true"),
    dict(lat=37.55, lng=127.25, radiusKm=20, page=3),
], query=lambda r: r)
api("GET /api/spots/{spotId}", rows="SELECT id spotId FROM spot WHERE status='ACTIVE' AND id % 4999 = 7 LIMIT 150")
api("GET /api/spots/{spotId}/reviews",
    rows="(SELECT spot_id spotId FROM spot_review GROUP BY spot_id ORDER BY COUNT(*) DESC LIMIT 1) UNION ALL "
         "(SELECT DISTINCT spot_id FROM spot_review WHERE id % 997 = 3 LIMIT 50)")
api("GET /api/spots/{spotId}/weather", rows="SELECT id spotId FROM spot WHERE status='ACTIVE' AND type='CAMPSITE' AND id % 397 = 7 LIMIT 40")
KOREA = dict(swLat=33.0, swLng=124.0, neLat=39.0, neLng=132.0)
api("GET /api/basecamps", rows=[
    dict(KOREA),
    dict(KOREA, page=3),
    dict(KOREA, spotTypes="BAKJI"),
    dict(lat=37.55, lng=127.25, radiusKm=30),
    dict(KOREA, fromDate=days(5), toDate=days(40)),
    dict(KOREA, hasVacancy="true"),
    dict(swLat=37.2, swLng=126.8, neLat=37.9, neLng=127.7),
    dict(KOREA, actor=HEAVY),
], query=lambda r: r)
api("GET /api/basecamps/{basecampId}", rows="SELECT id basecampId, IF(id % 2 = 0, 'bg0000001@loadtest.invalid', '') actor FROM basecamp WHERE id % 1499 = 3 LIMIT 150")
api("GET /api/programs", rows=[{}, dict(status="OPEN"), dict(status="CLOSED"), dict(page=5), dict(status="UPCOMING"), dict(actor=USER1)], query=lambda r: r)
api("GET /api/programs/{programId}", rows="SELECT id programId, IF(id % 2 = 0, 'user000001@loadtest.invalid', '') actor FROM program WHERE status='SCHEDULED' AND id % 11 = 3 LIMIT 100")
api("GET /api/community/posts", rows=[
    {},
    dict(cursor="__AFTER_60__"),
    dict(cursor="__AFTER_1000__"),
    dict(popular="true"),
    dict(keyword="lt희귀토큰", searchType="TITLE_CONTENT"),
    dict(keyword="백패킹", searchType="CONTENT"),
    dict(keyword="측정", searchType="TITLE"),
    dict(spotId="__SPOT__"),
    dict(actor=HEAVY),
], query=lambda r: r, note="글 목록, 검색, 인기순, 장소별을 섞는다")
api("GET /api/community/posts/{postId}",
    rows="(SELECT id postId FROM community_post WHERE status='ACTIVE' ORDER BY id DESC LIMIT 5) UNION ALL "
         "(SELECT id FROM community_post WHERE status='ACTIVE' AND id % 1999 = 11 LIMIT 95)".replace("SELECT id postId", "SELECT id postId"))
api("GET /api/community/posts/{postId}/comments",
    rows="(SELECT p.id postId FROM community_post p JOIN community_comment c ON c.post_id=p.id WHERE p.status='ACTIVE' GROUP BY p.id ORDER BY COUNT(*) DESC LIMIT 5) UNION ALL "
         "(SELECT id FROM community_post WHERE status='ACTIVE' AND id % 1999 = 11 LIMIT 45)")
api("GET /api/memories/feed", rows=[{}, dict(page=3), dict(page=50), dict(page=500), dict(actor=HEAVY)], query=lambda r: r)
api("GET /api/memories/{memoryId}", rows="SELECT id memoryId, IF(id % 2 = 0, 'bg0000001@loadtest.invalid', '') actor FROM memory WHERE visibility='PUBLIC' AND status='ACTIVE' AND id % 397 = 3 LIMIT 100")
api("GET /api/members/{memberId}/memories", rows="(SELECT id memberId FROM member WHERE email='user000001@loadtest.invalid') UNION ALL (SELECT DISTINCT member_id FROM memory WHERE id % 397 = 3 LIMIT 50)")
api("GET /api/members/{memberId}/profile", rows="SELECT id memberId FROM member WHERE email LIKE 'bg00000%' LIMIT 100")

# 조회 (로그인)
for key in ["GET /api/me", "GET /api/me/trust", "GET /api/me/basecamps", "GET /api/me/companion-reviews/pending",
            "GET /api/me/companion-reviews/received", "GET /api/me/notification-settings", "GET /api/me/solo-trips"]:
    api(key, rows=f"SELECT email actor FROM member WHERE email LIKE 'bg00000%' AND status='ACTIVE' LIMIT 40")
api("GET /api/me/memories", rows=[dict(actor=USER1)])
api("GET /api/me/notifications", rows=[dict(actor=USER1, page=0), dict(actor=USER1, page=3), dict(actor=USER1, page=100), dict(actor=HEAVY, page=0)],
    query=lambda r: {"page": r["page"]})
api("GET /api/me/notifications/unread-count", rows=[dict(actor=USER1), dict(actor=HEAVY)])
api("GET /api/me/program-applications", rows="SELECT email actor FROM member WHERE email LIKE 'user00000%' LIMIT 40")
api("GET /api/members/{memberId}/companion-reviews", actor=HEAVY,
    rows="SELECT reviewee_id memberId FROM companion_review WHERE id % 9973 = 1 LIMIT 40")
api("GET /api/basecamps/{basecampId}/applications",
    rows="(SELECT b.id basecampId, m.email actor FROM basecamp b JOIN member m ON m.id=b.leader_id "
         "JOIN basecamp_application a ON a.basecamp_id=b.id WHERE b.status='RECRUITING' AND m.status='ACTIVE' GROUP BY b.id, m.email ORDER BY COUNT(*) DESC LIMIT 20)")

# 관리자 조회
api("GET /api/admin/audit-logs", actor=ADMIN, rows=[{}, dict(page=3), dict(targetType="SPOT"), dict(page=200), dict(**{"from": iso(now() - timedelta(days=30))})], query=lambda r: r)
api("GET /api/admin/community/posts", actor=ADMIN, rows=[{}, dict(status="HIDDEN"), dict(status="PENDING_REVIEW"), dict(page=20)], query=lambda r: r)
api("GET /api/admin/community/comments", actor=ADMIN, rows=[{}, dict(status="HIDDEN"), dict(status="PENDING_REVIEW"), dict(page=20)], query=lambda r: r)
api("GET /api/admin/member-reports", actor=ADMIN, rows=[{}, dict(status="RECEIVED"), dict(urgent="true"), dict(page=20)], query=lambda r: r)
api("GET /api/admin/member-reports/{id}", actor=ADMIN, rows="SELECT id FROM member_report WHERE id % 197 = 3 LIMIT 100")
api("GET /api/admin/memories", actor=ADMIN, rows=[{}, dict(status="HIDDEN"), dict(status="PENDING_REVIEW"), dict(page=20)], query=lambda r: r)
api("GET /api/admin/programs/{programId}/applications", actor=ADMIN, rows="SELECT program_id programId FROM program_application GROUP BY program_id ORDER BY COUNT(*) DESC LIMIT 30")
api("GET /api/admin/spots", actor=ADMIN, rows=[{}, dict(status="HIDDEN"), dict(status="PENDING_REVIEW"), dict(page=20)], query=lambda r: r)
api("GET /api/admin/sync-jobs", actor=ADMIN, rows=[{}, dict(jobType="GOCAMPING"), dict(page=5)], query=lambda r: r)

# 인증과 회원
api("POST /api/auth/login", consume=False,
    rows="SELECT email FROM member WHERE email LIKE 'bg00001%' LIMIT 100",
    body=lambda r, i: {"email": r["email"], "password": PASSWORD})
api("POST /api/auth/logout", consume=True, n=WRITE_ROWS,
    rows="SELECT email actor FROM member WHERE email LIKE 'bg0002%' AND status='ACTIVE' LIMIT 230")
api("POST /api/auth/password-reset/request", consume=True, n=WRITE_ROWS,
    rows="SELECT email FROM member WHERE email LIKE 'bg0003%' AND status='ACTIVE' LIMIT 230",
    direct=True, body=lambda r, i: {"email": r["email"]})
api("POST /api/auth/password-reset/confirm", consume=True, n=WRITE_ROWS,
    rows="SELECT RPAD(CONCAT('ltreset', s.n), 43, 'x') token FROM (SELECT ROW_NUMBER() OVER () - 1 n FROM member LIMIT 230) s",
    body=lambda r, i: {"token": r["token"], "newPassword": NEW_PASSWORD, "newPasswordConfirm": NEW_PASSWORD})
api("PUT /api/me/password", consume=True, n=WRITE_ROWS,
    rows="SELECT email actor FROM member WHERE email LIKE 'bg0004%' AND status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"currentPassword": PASSWORD, "newPassword": NEW_PASSWORD, "newPasswordConfirm": NEW_PASSWORD})
api("POST /api/members", consume=True, n=WRITE_ROWS,
    rows=[dict(k=i) for i in range(WRITE_ROWS)],
    body=lambda r, i: {"email": f"sweep-{RUN}-{r['k']}@example.com", "password": NEW_PASSWORD, "passwordConfirm": NEW_PASSWORD,
                       "nickname": f"sw{RUN}{r['k']}", "ageConfirmed": True}, direct=True)
api("POST /api/me/email-verification", consume=True, n=WRITE_ROWS,
    rows="SELECT email actor FROM member WHERE email LIKE 'unv-new-%' LIMIT 230",
    body=lambda r, i: {"code": "123456"})
api("POST /api/me/email-verification/resend", consume=True, n=WRITE_ROWS,
    rows="SELECT email actor FROM member WHERE email LIKE 'unv-new-%' ORDER BY email DESC LIMIT 230", direct=True)
api("POST /api/me/identity-verification", consume=True, n=WRITE_ROWS,
    rows="SELECT m.email actor FROM member m WHERE m.email LIKE 'noid-%' LIMIT 230",
    body=lambda r, i: {"birthYear": 1990, "gender": "FEMALE", "demoIdentityKey": f"sweep-{RUN}-{r['actor']}"})
api("PATCH /api/me", consume=True, n=WRITE_ROWS, rows="SELECT email actor FROM member WHERE email LIKE 'bg0005%' AND status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"selfAgeGroup": "THIRTIES"} if i % 2 else {"nickname": f"pm{RUN}{i}"})
api("DELETE /api/me", consume=True, n=WRITE_ROWS,
    rows="SELECT m.email actor FROM member m WHERE m.email LIKE 'bg0006%' AND m.status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"password": PASSWORD})
api("PUT /api/me/notification-settings", consume=True, n=WRITE_ROWS, rows="SELECT email actor FROM member WHERE email LIKE 'bg0007%' AND status='ACTIVE' LIMIT 230",
    body=lambda r, i: [{"type": "PROGRAM_APPLICATION_CONFIRMED", "emailEnabled": False}])
api("POST /api/me/notifications/read-all", consume=True, n=WRITE_ROWS, rows="SELECT email actor FROM member WHERE email LIKE 'bg0008%' AND status='ACTIVE' LIMIT 230")
api("POST /api/me/notifications/{notificationId}/read", consume=True, n=WRITE_ROWS,
    rows="SELECT n.id notificationId, m.email actor FROM notification n JOIN member m ON m.id=n.member_id WHERE n.read_at IS NULL AND n.id % 1009 = 7 AND m.status='ACTIVE' LIMIT 230")
api("POST /api/spots/ai-interpret", rows="SELECT email actor FROM member WHERE email LIKE 'bg00009%' AND status='ACTIVE' LIMIT 40",
    body=lambda r, i: {"query": "강릉 근처 물 있는 박지"})

# 장소, 후기, 박지
BAKJI_ROWS = "SELECT s.id spotId, m.email actor FROM spot s JOIN bakji_detail d ON d.spot_id=s.id JOIN member m ON m.id=d.reporter_id WHERE s.status='ACTIVE' AND {P:s.id} AND s.id % 37 = 5 LIMIT 230"
api("POST /api/bakjis", consume=True, n=WRITE_ROWS,
    rows="SELECT email actor FROM member WHERE email LIKE 'bg0010%' AND status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"name": f"측정 박지 {RUN}-{i}", "lat": round(36.0 + (i % 200) * 0.011, 5), "lng": round(127.0 + (i % 17) * 0.13 + RUN % 7 * 0.003, 5),
                       "description": "측정용", "hasWater": True, "hasToilet": False})
api("PATCH /api/bakjis/{spotId}", consume=True, n=WRITE_ROWS, rows=BAKJI_ROWS, body=lambda r, i: {"description": f"수정 {i}"})
api("DELETE /api/bakjis/{spotId}", consume=True, n=WRITE_ROWS, rows=BAKJI_ROWS)
api("POST /api/bakjis/{spotId}/confirmations", consume=True, n=WRITE_ROWS,
    rows="SELECT s.id spotId, m.email actor FROM (SELECT id FROM spot WHERE type='BAKJI' AND status='ACTIVE' AND id % 37 = 11 AND {P:id} LIMIT 230) s "
         "JOIN member m ON m.email = CONCAT('bg', LPAD(1000 + s.id % 90000, 7, '0'), '@loadtest.invalid') WHERE m.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM bakji_confirmation c WHERE c.spot_id=s.id AND c.member_id=m.id)")
api("POST /api/bakjis/{spotId}/reports", consume=True, n=WRITE_ROWS,
    rows="SELECT s.id spotId, m.email actor FROM (SELECT id FROM spot WHERE type='BAKJI' AND status='ACTIVE' AND id % 37 = 13 AND {P:id} LIMIT 230) s "
         "JOIN member m ON m.email = CONCAT('bg', LPAD(1000 + s.id % 90000, 7, '0'), '@loadtest.invalid') WHERE m.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM bakji_report c WHERE c.spot_id=s.id AND c.reporter_id=m.id)",
    body=lambda r, i: {"reason": "CLOSED", "content": "측정용 신고"})
api("POST /api/spots/{spotId}/reviews", consume=True, n=WRITE_ROWS,
    rows="SELECT s.id spotId, m.email actor, ri.id imageId FROM (SELECT id FROM spot WHERE status='ACTIVE' AND id % 37 = 17 AND {P:id} LIMIT 230) s "
         "JOIN (SELECT ROW_NUMBER() OVER (ORDER BY id) rn, id, member_id FROM review_image WHERE review_id IS NULL AND object_key LIKE '%lt-orphan%' AND created_at > UTC_TIMESTAMP() - INTERVAL 1 DAY LIMIT 240) ri ON ri.rn = 1 + s.id % 240 "
         "JOIN member m ON m.id = ri.member_id WHERE m.status='ACTIVE' GROUP BY m.id LIMIT 230",
    body=lambda r, i: {"visitedDate": days(-3), "rating": 5, "content": "측정용 후기", "imageIds": [int(r["imageId"])]}, note="사진 한 장을 붙여 S3 HEAD 경로까지 탄다")
api("PATCH /api/reviews/{reviewId}", consume=True, n=WRITE_ROWS,
    rows="SELECT r.id reviewId, m.email actor FROM spot_review r JOIN member m ON m.id=r.member_id WHERE m.status='ACTIVE' AND {P:r.id} AND r.id % 13 = 1 LIMIT 230",
    body=lambda r, i: {"rating": 4, "content": "수정한 측정용 후기"})
api("DELETE /api/reviews/{reviewId}", consume=True, n=WRITE_ROWS,
    rows="SELECT r.id reviewId, m.email actor FROM spot_review r JOIN member m ON m.id=r.member_id WHERE m.status='ACTIVE' AND {P:r.id} AND r.id % 13 = 2 LIMIT 230")
api("POST /api/reviews/images", consume=True, n=WRITE_ROWS, rows="SELECT email actor FROM member WHERE email LIKE 'bg0011%' AND status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"contentType": "image/jpeg", "sizeBytes": 200000})
api("POST /api/solo-trips", consume=True, idem=True, n=WRITE_ROWS,
    rows="SELECT m.email actor, s.id spotId FROM member m JOIN spot s ON s.id = (SELECT id FROM spot WHERE status='ACTIVE' AND type='BAKJI' AND id > 5000 + m.id % 10000 LIMIT 1) WHERE m.email LIKE 'bg0012%' AND m.status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"spotId": r["spotId"], "startDate": days(20 + i % 30), "endDate": days(21 + i % 30)})
api("DELETE /api/solo-trips/{soloTripId}", consume=True, n=WRITE_ROWS,
    rows="SELECT t.id soloTripId, m.email actor FROM solo_trip t JOIN member m ON m.id=t.member_id WHERE m.status='ACTIVE' AND t.end_date > UTC_DATE() AND {P:t.id} LIMIT 230")

# 베이스캠프
RECRUITING_LEADER = ("SELECT b.id basecampId, m.email actor FROM basecamp b JOIN member m ON m.id=b.leader_id "
                     "WHERE b.status='RECRUITING' AND m.status='ACTIVE' AND {P:b.id} LIMIT 230")
api("POST /api/basecamps", consume=True, idem=True, n=WRITE_ROWS,
    rows="SELECT m.email actor, s.id spotId FROM member m JOIN spot s ON s.id = (SELECT id FROM spot WHERE status='ACTIVE' AND id > 5000 + m.id % 10000 LIMIT 1) WHERE m.email LIKE 'bg0013%' AND m.status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"spotId": r["spotId"], "title": f"측정 캠프 {i}", "description": "측정용 캠프입니다.", "startDate": days(20 + i % 30), "endDate": days(21 + i % 30), "capacity": 4})
api("PATCH /api/basecamps/{basecampId}", consume=True, n=WRITE_ROWS, rows=RECRUITING_LEADER, body=lambda r, i: {"description": f"수정한 설명 {i}"})
api("PUT /api/basecamps/{basecampId}/contact", consume=True, n=WRITE_ROWS, rows=RECRUITING_LEADER, body=lambda r, i: {"contactInfo": "https://open.kakao.com/o/sweep"})
api("POST /api/basecamps/{basecampId}/close", consume=True, n=WRITE_ROWS,
    rows="SELECT b.id basecampId, m.email actor FROM basecamp b JOIN member m ON m.id=b.leader_id WHERE b.status='RECRUITING' AND m.status='ACTIVE' AND {P:b.id} AND EXISTS (SELECT 1 FROM basecamp_member bm WHERE bm.basecamp_id=b.id AND bm.status='ACTIVE' AND bm.role='MEMBER') LIMIT 230".replace('AND {P:b.id} ', 'AND MOD(b.id, 40) = 3 '))
api("POST /api/basecamps/{basecampId}/reopen", consume=True, n=WRITE_ROWS,
    rows="SELECT b.id basecampId, m.email actor FROM basecamp b JOIN member m ON m.id=b.leader_id WHERE b.status='CLOSED' AND b.closed_reason='LEADER' AND m.status='ACTIVE' LIMIT 230")
api("POST /api/basecamps/{basecampId}/confirm", consume=True, n=WRITE_ROWS,
    rows="SELECT b.id basecampId, m.email actor FROM basecamp b JOIN member m ON m.id=b.leader_id WHERE b.status IN ('RECRUITING','CLOSED') AND m.status='ACTIVE' AND {P:b.id} AND (SELECT COUNT(*) FROM basecamp_member bm WHERE bm.basecamp_id=b.id AND bm.status='ACTIVE') >= 2 LIMIT 230".replace('AND {P:b.id} ', 'AND MOD(b.id, 40) = 7 '))
api("POST /api/basecamps/{basecampId}/cancel", consume=True, n=WRITE_ROWS, rows=RECRUITING_LEADER)
api("POST /api/basecamps/{basecampId}/applications", consume=True, n=WRITE_ROWS,
    rows="SELECT b.id basecampId, m.email actor FROM (SELECT id FROM basecamp WHERE status='RECRUITING' AND (min_trust_level IS NULL OR min_trust_level = 1) AND age_group_min IS NULL AND same_gender_only=0 AND {P:id} LIMIT 230) b "
         "JOIN member m ON m.email = CONCAT('bg', LPAD(100000 + b.id % 40000, 7, '0'), '@loadtest.invalid') WHERE m.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM basecamp_application a WHERE a.basecamp_id=b.id AND a.applicant_id=m.id AND a.status='PENDING') AND NOT EXISTS (SELECT 1 FROM basecamp_member bm WHERE bm.basecamp_id=b.id AND bm.member_id=m.id)",
    body=lambda r, i: {"message": "함께 가고 싶습니다."})
PENDING = ("SELECT a.basecamp_id basecampId, a.id applicationId, l.email actor FROM basecamp_application a JOIN basecamp b ON b.id=a.basecamp_id AND b.status='RECRUITING' AND (SELECT COUNT(*) FROM basecamp_member h WHERE h.basecamp_id=b.id AND h.status='ACTIVE') < b.capacity "
           "JOIN member l ON l.id=b.leader_id JOIN member ap ON ap.id=a.applicant_id WHERE a.status='PENDING' AND l.status='ACTIVE' AND ap.status='ACTIVE' AND {P:a.id} GROUP BY a.basecamp_id LIMIT 230")
api("POST /api/basecamps/{basecampId}/applications/{applicationId}/approve", consume=True, n=WRITE_ROWS, rows=PENDING)
api("POST /api/basecamps/{basecampId}/applications/{applicationId}/reject", consume=True, n=WRITE_ROWS, rows=PENDING)
api("DELETE /api/basecamps/{basecampId}/applications/me", consume=True, n=WRITE_ROWS,
    rows="SELECT a.basecamp_id basecampId, ap.email actor FROM basecamp_application a JOIN basecamp b ON b.id=a.basecamp_id AND b.status='RECRUITING' JOIN member ap ON ap.id=a.applicant_id WHERE a.status='PENDING' AND ap.status='ACTIVE' AND {P:a.id} LIMIT 230")
MEMBER_ROWS = ("SELECT bm.basecamp_id basecampId, bm.member_id memberId, {who}.email actor FROM basecamp_member bm JOIN basecamp b ON b.id=bm.basecamp_id AND b.status='RECRUITING' AND b.start_date > UTC_DATE() + INTERVAL 3 DAY "
               "JOIN member me ON me.id=bm.member_id JOIN member l ON l.id=b.leader_id WHERE bm.status='ACTIVE' AND bm.role='MEMBER' AND me.status='ACTIVE' AND l.status='ACTIVE' AND MOD(bm.basecamp_id, 40) = {side} GROUP BY bm.basecamp_id LIMIT 230")
api("DELETE /api/basecamps/{basecampId}/members/me", consume=True, n=WRITE_ROWS, rows=MEMBER_ROWS.replace("{who}", "me").replace("{side}", "11"))
api("POST /api/basecamps/{basecampId}/members/{memberId}/kick", consume=True, n=WRITE_ROWS, rows=MEMBER_ROWS.replace("{who}", "l").replace("{side}", "13"), body=lambda r, i: {"reason": "NO_CONTACT"})
api("POST /api/basecamps/{basecampId}/companion-reviews", consume=True, n=WRITE_ROWS,
    rows="SELECT a.basecamp_id basecampId, a.member_id reviewee, r.email actor FROM basecamp_member a JOIN basecamp b ON b.id=a.basecamp_id AND b.status='COMPLETED' AND b.completed_at > UTC_TIMESTAMP() - INTERVAL 14 DAY "
         "JOIN basecamp_member c ON c.basecamp_id=a.basecamp_id AND c.member_id<>a.member_id AND c.status='ACTIVE' JOIN member r ON r.id=c.member_id AND r.status='ACTIVE' "
         "WHERE a.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM companion_review x WHERE x.basecamp_id=a.basecamp_id AND x.reviewer_id=c.member_id AND x.reviewee_id=a.member_id) GROUP BY a.basecamp_id LIMIT 230",
    body=lambda r, i: {"revieweeId": r["reviewee"], "rejoinWanted": True, "tags": ["ON_TIME"], "comment": "측정용 후기"})
api("POST /api/member-reports", consume=True, n=WRITE_ROWS,
    rows="SELECT a.basecamp_id basecampId, a.member_id target, r.email actor FROM basecamp_member a JOIN basecamp b ON b.id=a.basecamp_id AND b.status='COMPLETED' AND b.completed_at > UTC_TIMESTAMP() - INTERVAL 14 DAY "
         "JOIN basecamp_member c ON c.basecamp_id=a.basecamp_id AND c.member_id<>a.member_id AND c.status='ACTIVE' JOIN member r ON r.id=c.member_id AND r.status='ACTIVE' "
         "WHERE a.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM member_report x WHERE x.basecamp_id=a.basecamp_id AND x.reporter_id=c.member_id AND x.target_member_id=a.member_id) GROUP BY a.basecamp_id LIMIT 230",
    body=lambda r, i: {"targetMemberId": r["target"], "basecampId": r["basecampId"], "kind": "MEMBER", "type": "NO_SHOW", "content": "측정용 신고"})

# 커뮤니티
OWN_POST = "SELECT p.id postId, m.email actor FROM community_post p JOIN member m ON m.id=p.member_id WHERE p.status='ACTIVE' AND m.status='ACTIVE' AND {P:p.id} LIMIT 230"
OWN_COMMENT = "SELECT c.id commentId, m.email actor FROM community_comment c JOIN community_post p ON p.id=c.post_id AND p.status='ACTIVE' JOIN member m ON m.id=c.member_id WHERE c.status='ACTIVE' AND m.status='ACTIVE' AND {P:c.id} LIMIT 230"
OTHER = "JOIN member m ON m.email = CONCAT('bg', LPAD(1000 + {src} % 90000, 7, '0'), '@loadtest.invalid') AND m.status='ACTIVE'"
api("POST /api/community/posts", consume=True, idem=True, n=WRITE_ROWS,
    rows="SELECT i.id imageId, m.email actor FROM community_image i JOIN member m ON m.id=i.member_id AND m.status='ACTIVE' WHERE i.post_id IS NULL AND i.object_key LIKE '%lt-orphan%' AND i.created_at > UTC_TIMESTAMP() - INTERVAL 1 DAY GROUP BY m.id LIMIT 230",
    body=lambda r, i: {"title": f"측정 글 {i}", "content": "측정용 글입니다. 백패킹 다녀온 이야기.", "imageIds": [int(r["imageId"])]}, note="사진 한 장을 붙여 S3 HEAD 경로까지 탄다")
api("PATCH /api/community/posts/{postId}", consume=True, n=WRITE_ROWS, rows=OWN_POST, body=lambda r, i: {"content": f"수정한 글 {i}"})
api("DELETE /api/community/posts/{postId}", consume=True, n=WRITE_ROWS, rows=OWN_POST)
api("POST /api/community/posts/{postId}/comments", consume=True, idem=True, n=WRITE_ROWS,
    rows="SELECT p.id postId, m.email actor FROM (SELECT id FROM community_post WHERE status='ACTIVE' AND {P:id} LIMIT 230) p " + OTHER.replace("{src}", "p.id"),
    body=lambda r, i: {"content": f"측정 댓글 {i}"})
api("PATCH /api/community/comments/{commentId}", consume=True, n=WRITE_ROWS, rows=OWN_COMMENT, body=lambda r, i: {"content": f"수정 댓글 {i}"})
api("DELETE /api/community/comments/{commentId}", consume=True, n=WRITE_ROWS, rows=OWN_COMMENT)
api("POST /api/community/images", consume=True, n=WRITE_ROWS, rows="SELECT email actor FROM member WHERE email LIKE 'bg0015%' AND status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"contentType": "image/jpeg", "sizeBytes": 200000, "thumbnailSizeBytes": 20000})
api("PUT /api/community/posts/{postId}/like", consume=True, n=WRITE_ROWS,
    rows="SELECT p.id postId, m.email actor FROM (SELECT id FROM community_post WHERE status='ACTIVE' AND {P:id} LIMIT 230) p " + OTHER.replace("{src}", "p.id") + " WHERE NOT EXISTS (SELECT 1 FROM community_post_like l WHERE l.post_id=p.id AND l.member_id=m.id)")
api("DELETE /api/community/posts/{postId}/like", consume=True, n=WRITE_ROWS,
    rows="SELECT l.post_id postId, m.email actor FROM community_post_like l JOIN community_post p ON p.id=l.post_id AND p.status='ACTIVE' JOIN member m ON m.id=l.member_id AND m.status='ACTIVE' WHERE {P:l.post_id} LIMIT 230")
api("POST /api/community/posts/{postId}/reports", consume=True, n=WRITE_ROWS,
    rows="SELECT p.id postId, m.email actor FROM (SELECT id FROM community_post WHERE status='ACTIVE' AND {P:id} LIMIT 230) p " + OTHER.replace("{src}", "p.id") + " WHERE NOT EXISTS (SELECT 1 FROM community_report l WHERE l.target_type='POST' AND l.target_id=p.id AND l.reporter_id=m.id)",
    body=lambda r, i: {"reason": "SPAM", "content": "측정용 신고"})
api("POST /api/community/comments/{commentId}/reports", consume=True, n=WRITE_ROWS,
    rows="SELECT p.id commentId, m.email actor FROM (SELECT c.id FROM community_comment c JOIN community_post cp ON cp.id=c.post_id AND cp.status='ACTIVE' WHERE c.status='ACTIVE' AND {P:c.id} LIMIT 230) p " + OTHER.replace("{src}", "p.id") + " WHERE NOT EXISTS (SELECT 1 FROM community_report l WHERE l.target_type='COMMENT' AND l.target_id=p.id AND l.reporter_id=m.id)",
    body=lambda r, i: {"reason": "SPAM", "content": "측정용 신고"})

# 추억 기록
OWN_MEM = "SELECT x.id memoryId, m.email actor FROM memory x JOIN member m ON m.id=x.member_id WHERE x.status='ACTIVE' AND m.status='ACTIVE' AND {P:x.id} LIMIT 230"
PUB_MEM = "(SELECT id FROM memory WHERE status='ACTIVE' AND visibility='PUBLIC' AND {P:id} LIMIT 230)"
api("POST /api/memories", consume=True, idem=True, n=WRITE_ROWS,
    rows="SELECT i.id imageId, m.email actor FROM memory_image i JOIN member m ON m.id=i.member_id AND m.status='ACTIVE' WHERE i.memory_id IS NULL AND i.object_key LIKE '%lt-orphan%' AND i.created_at > UTC_TIMESTAMP() - INTERVAL 1 DAY GROUP BY m.id LIMIT 230",
    body=lambda r, i: {"lat": 37.5, "lng": 127.1, "visitedDate": days(-2), "nights": 1, "content": "측정용 기록", "visibility": "PUBLIC", "imageIds": [int(r["imageId"])]}, note="사진 한 장을 붙여 S3 HEAD 경로까지 탄다")
api("PATCH /api/memories/{memoryId}", consume=True, n=WRITE_ROWS, rows=OWN_MEM, body=lambda r, i: {"content": f"수정 기록 {i}"})
api("DELETE /api/memories/{memoryId}", consume=True, n=WRITE_ROWS, rows=OWN_MEM)
api("POST /api/memories/images", consume=True, n=WRITE_ROWS, rows="SELECT email actor FROM member WHERE email LIKE 'bg0017%' AND status='ACTIVE' LIMIT 230",
    body=lambda r, i: {"contentType": "image/jpeg", "sizeBytes": 200000, "thumbnailSizeBytes": 20000})
api("PUT /api/memories/{memoryId}/like", consume=True, n=WRITE_ROWS,
    rows="SELECT x.id memoryId, m.email actor FROM " + PUB_MEM + " x " + OTHER.replace("{src}", "x.id") + " WHERE NOT EXISTS (SELECT 1 FROM memory_like l WHERE l.memory_id=x.id AND l.member_id=m.id)")
api("DELETE /api/memories/{memoryId}/like", consume=True, n=WRITE_ROWS,
    rows="SELECT l.memory_id memoryId, m.email actor FROM memory_like l JOIN memory x ON x.id=l.memory_id AND x.status='ACTIVE' AND x.visibility='PUBLIC' JOIN member m ON m.id=l.member_id AND m.status='ACTIVE' WHERE {P:l.memory_id} LIMIT 230")
api("POST /api/memories/{memoryId}/reports", consume=True, n=WRITE_ROWS,
    rows="SELECT x.id memoryId, m.email actor FROM " + PUB_MEM + " x " + OTHER.replace("{src}", "x.id") + " WHERE NOT EXISTS (SELECT 1 FROM memory_report l WHERE l.memory_id=x.id AND l.reporter_id=m.id)",
    body=lambda r, i: {"reason": "SPAM", "content": "측정용 신고"})

# 행사
api("POST /api/programs/{programId}/applications", consume=True, idem=True, n=WRITE_ROWS,
    rows="SELECT m.email actor, p.id programId FROM member m JOIN (SELECT MAX(id) id FROM program WHERE title LIKE 'sweep-open%') p WHERE m.email LIKE 'bg0018%' AND m.status='ACTIVE' LIMIT 230")
PENDING_APP = "SELECT a.id applicationId, m.email actor FROM program_application a JOIN member m ON m.id=a.member_id AND m.status='ACTIVE' WHERE a.status='PENDING_PAYMENT' AND a.payment_due_at > UTC_TIMESTAMP() AND a.program_id IN (SELECT id FROM program WHERE title LIKE 'sweep-open%') AND MOD(a.id, 2) = {side} LIMIT 230"
api("POST /api/program-applications/{applicationId}/pay", consume=True, idem=True, n=WRITE_ROWS, rows=PENDING_APP.replace('{side}', '0'), body=lambda r, i: {"method": "FAKE_CARD"})
api("POST /api/program-applications/{applicationId}/cancel", consume=True, n=WRITE_ROWS, rows="(" + PENDING_APP.replace('{side}', '1') + ") UNION ALL (SELECT a.id, m.email FROM program_application a JOIN member m ON m.id=a.member_id JOIN program p ON p.id=a.program_id WHERE p.title='sweep-full-1')", note="맨 뒤 행은 정원이 찬 행사의 확정 신청이라 빈자리 알림 경로까지 탄다")
api("POST /api/programs/{programId}/vacancy-alerts", consume=True, n=WRITE_ROWS,
    rows="SELECT m.email actor, p.id programId FROM member m JOIN (SELECT id FROM program WHERE title = 'sweep-full-0') p WHERE m.email LIKE 'bg0019%' AND m.status='ACTIVE' LIMIT 230")
api("DELETE /api/programs/{programId}/vacancy-alerts", consume=True, n=WRITE_ROWS,
    rows="SELECT v.program_id programId, m.email actor FROM program_vacancy_alert v JOIN member m ON m.id=v.member_id AND m.status='ACTIVE' WHERE v.notified_at IS NULL AND v.id % 3 = 1 LIMIT 230")

# 관리자 쓰기
def program_body(r, i):
    t = now()
    return {"title": f"측정 행사 {RUN}-{i}", "description": "측정용 행사", "locationText": "측정", "startAt": iso(t + timedelta(days=3)), "endAt": iso(t + timedelta(days=4)),
            "capacity": 50, "fee": 30000, "applyOpenAt": iso(t + timedelta(hours=1)), "applyCloseAt": iso(t + timedelta(days=2)), "paymentDeadlineMinutes": 30, "overnight": False}


api("POST /api/admin/programs", consume=True, n=WRITE_ROWS, actor=ADMIN, rows=[dict(k=i) for i in range(WRITE_ROWS)], body=program_body)
api("PATCH /api/admin/programs/{programId}", consume=True, n=WRITE_ROWS, actor=ADMIN,
    rows="SELECT id programId FROM program WHERE title LIKE 'sweep-edit%' LIMIT 230",
    body=lambda r, i: {"description": f"수정한 설명 {i}"})
api("POST /api/admin/programs/{programId}/cancel", consume=True, n=WRITE_ROWS, actor=ADMIN,
    rows="SELECT id programId FROM program WHERE title LIKE 'sweep-cancel%' LIMIT 230", note="행사마다 신청 수가 다르면 비용이 달라지므로 신청 300건 행사를 쓴다")
api("POST /api/admin/community/posts/{postId}/hide", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT id postId FROM community_post WHERE status='ACTIVE' AND {P:id} LIMIT 230")
api("POST /api/admin/community/posts/{postId}/restore", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT id postId FROM community_post WHERE status='HIDDEN' LIMIT 230")
api("POST /api/admin/community/comments/{commentId}/hide", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT id commentId FROM community_comment WHERE status='ACTIVE' AND {P:id} LIMIT 230")
api("POST /api/admin/community/comments/{commentId}/restore", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT id commentId FROM community_comment WHERE status='HIDDEN' LIMIT 230")
api("POST /api/admin/memories/{memoryId}/hide", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT id memoryId FROM memory WHERE status='ACTIVE' AND {P:id} LIMIT 230")
api("POST /api/admin/memories/{memoryId}/restore", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT id memoryId FROM memory WHERE status='HIDDEN' LIMIT 230")
api("POST /api/admin/spots/{id}/hide", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT id FROM spot WHERE status='ACTIVE' AND type='BAKJI' AND {P:id} AND id % 37 = 19 LIMIT 230")
api("POST /api/admin/spots/{id}/restore", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT id FROM spot WHERE status='HIDDEN' AND type='BAKJI' AND {P:id} LIMIT 230")
OPEN_REPORT = "SELECT r.id FROM member_report r JOIN member t ON t.id=r.target_member_id AND t.status='ACTIVE' WHERE r.status='{st}' AND MOD(r.id, 3) = {side} LIMIT 230"
api("POST /api/admin/member-reports/{id}/start-review", consume=True, n=WRITE_ROWS, actor=ADMIN, rows=OPEN_REPORT.replace('{side}', '0').replace('{st}', 'RECEIVED'))
api("POST /api/admin/member-reports/{id}/dismiss", consume=True, n=WRITE_ROWS, actor=ADMIN, rows=OPEN_REPORT.replace('{side}', '1').replace('{st}', 'IN_REVIEW'), body=lambda r, i: {"note": "측정용 기각"})
api("POST /api/admin/member-reports/{id}/action", consume=True, n=WRITE_ROWS, actor=ADMIN, rows="SELECT r.id, ELT(1 + LEAST(3, COALESCE((SELECT MAX(s.level) FROM sanction s WHERE s.member_id=t.id AND s.status<>'REVOKED'), 0)), 'WARNING', 'SUSPEND_7D', 'SUSPEND_30D', 'PERMANENT') nextType FROM member_report r JOIN member t ON t.id=r.target_member_id AND t.status='ACTIVE' WHERE r.status='IN_REVIEW' AND MOD(r.id, 3) = 2 GROUP BY t.id LIMIT 230",
    body=lambda r, i: {"sanction": {"type": r["nextType"], "reason": "측정용 제재"}, "note": "측정용 조치"},
    note="제재 대상의 진행 중 캠프를 함께 정리하는 경로")
api("POST /api/admin/sanctions/{id}/lift", consume=True, n=WRITE_ROWS, actor=ADMIN,
    rows="SELECT id FROM sanction WHERE status='ACTIVE' AND type <> 'WARNING' LIMIT 230",
    body=lambda r, i: {"reason": "END_EARLY"})
api("POST /api/admin/sync-jobs", consume=True, actor=ADMIN, ok=[202],
    rows=[dict(jobType="GOCAMPING"), dict(jobType="FOREST"), dict(jobType="FOREST_STANDARD"), dict(jobType="PARK_BOUNDARY")],
    body=lambda r, i: {"jobType": r["jobType"]}, note="외부 공공데이터 API는 스텁, 파일 적재는 테스트 고정 파일이다")

RUN = 0


def set_run(run):
    global RUN
    RUN = run
