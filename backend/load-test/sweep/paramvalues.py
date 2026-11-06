"""render.py가 쓰는 대표 값. 매퍼 XML의 파라미터 이름을 시드의 실제 값으로 바꾼다."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import re
import subprocess


ID_SQL = {
    "memberId": "SELECT id FROM member WHERE email='bg0000001@loadtest.invalid'",
    "basecampId": "SELECT id FROM basecamp WHERE status='RECRUITING' LIMIT 1",
    "postId": "SELECT id FROM community_post WHERE status='ACTIVE' ORDER BY id DESC LIMIT 1",
    "commentId": "SELECT id FROM community_comment WHERE status='ACTIVE' LIMIT 1",
    "memoryId": "SELECT id FROM memory WHERE status='ACTIVE' LIMIT 1",
    "programId": "SELECT MAX(id) FROM program",
    "spotId": "SELECT id FROM spot WHERE status='ACTIVE' AND type='BAKJI' AND id > 5000 LIMIT 1",
    "applicationId": "SELECT id FROM program_application WHERE status='PENDING_PAYMENT' LIMIT 1",
    "reportId": "SELECT id FROM member_report LIMIT 1",
    "reviewId": "SELECT id FROM spot_review LIMIT 1",
    "protectedAreaId": "SELECT id FROM protected_area LIMIT 1",
    "companionReviewId": "SELECT id FROM companion_review LIMIT 1",
    "adminId": "SELECT id FROM member WHERE email='admin@loadtest.invalid'",
    "imageId": "SELECT id FROM memory_image LIMIT 1",
}

ALIAS = {
    "reporterId": "memberId", "revieweeId": "memberId", "reviewerId": "memberId", "viewerId": "memberId", "targetId": "postId",
    "id": "spotId", "externalId": None,
}


def base_values():
    out = {}
    for name, q in ID_SQL.items():
        p = subprocess.run([S + "/sql.sh", "--batch", "--skip-column-names"], input=q.encode(), capture_output=True)
        out[name] = p.stdout.decode().strip().split("\n")[0]
    return out


FIXED = {
    "limit": "1000", "offset": "0", "dup.limit": "20", "afterId": "0", "maxAttempts": "5", "maxPerTarget": "3",
    "kind": "'MEMBER'", "type": "'NO_SHOW'", "status": "'RECEIVED'", "visibility": "'PUBLIC'", "relation": "'MEMBER'",
    "source": "'KDPA'", "spotType": "'BAKJI'", "domain": "'example.com'", "key": "'lt-key'", "jobType": "'GOCAMPING'", "setting": "'MOUNTAIN'",
    "eventType": "'PROGRAM_APPLICATION_EXPIRED'", "aggregateType": "'PROGRAM_APPLICATION'", "error": "'lt'", "transientFailure": "1", "urgent": "1",
    "defaultEmailEnabled": "1", "requestIp": "'10.0.0.1'", "inputTokens": "900", "outputTokens": "40", "views": "1", "paymentDueAt": "UTC_TIMESTAMP()",
    "targetType": "'SPOT'", "fromDate": "CURDATE()", "toDate": "CURDATE() + INTERVAL 30 DAY", "lat": "37.55", "lng": "127.25", "centerLat": "37.55", "centerLng": "127.25", "radiusMeters": "20000", "excludeSpotId": "1",
    "swLat": "37.4", "swLng": "127.1", "neLat": "37.7", "neLng": "127.4",
    "retryDelayMillis": "1000", "retryMaxDelayMillis": "60000", "transientRetryWindowMillis": "60000",
    "cellLat": "0.25", "cellLng": "0.25", "lastCellIndex": "1000", "cellSize": "0.25", "fromColumn": "0", "toColumn": "10", "level": "8",
    "baseTrustLevel": "1", "maxTrustLevel": "2", "minLikeCount": "1", "minPostId": "1", "keyword": "'백패킹'", "version": "0",
    "areaType": "'NATIONAL_PARK'", "sourceDate": "CURDATE()", "name": "'측정용'", "boundaryWkt": "'MULTIPOLYGON(((127 37,127.1 37,127.1 37.1,127 37,127 37)))'",
    "address": "'주소'", "category": "'오토캠핑'", "closedFrom": "NULL", "closedUntil": "NULL", "facilities": "NULL", "homepage": "NULL", "latitude": "37.5", "longitude": "127.1",
    "operatingStatus": "'OPERATING'", "phone": "NULL", "weatherNx": "60", "weatherNy": "127", "date": "CURDATE()", "today": "CURDATE()", "dueStartDate": "CURDATE()", "night": "1",
}
TIMES = {"cutoff", "now", "since", "from", "to", "leaseUntil", "lockedUntil", "createdAfter", "createdBefore", "createdAt", "recentSince", "revealCutoff", "writableSince", "completedBefore"}
PAST = {"cutoff", "since", "from", "createdAfter", "createdBefore", "createdAt", "recentSince", "revealCutoff", "writableSince", "completedBefore"}


def value(qid, name, vals):
    last = name.split(".")[-1]
    full = name.split(",")[0].strip()
    if full in FIXED:
        return FIXED[full]
    if last in TIMES:
        if last in ("cutoff", "completedBefore"):
            return "UTC_TIMESTAMP() - INTERVAL 370 DAY" if "report" in qid.lower() or "Report" in qid else "UTC_TIMESTAMP() - INTERVAL 7 DAY"
        return "UTC_TIMESTAMP() - INTERVAL 1 HOUR" if last in PAST else "UTC_TIMESTAMP()"
    if last in FIXED:
        return FIXED[last]
    if last in vals:
        return vals[last]
    a = ALIAS.get(last)
    if a and a in vals:
        return vals[a]
    return None


FALSE_TESTS = {
    "mailLane and mailEventTypes.isEmpty()",
    "condition.keyword != null and condition.searchType == 'AUTHOR'",
    "condition.searchType == 'AUTHOR'",
    "condition.searchType == 'CONTENT'",
    "condition.searchType == 'TITLE'",
    "phase == @com.moewadeul.program.domain.ProgramPhase@UPCOMING",
    "phase == @com.moewadeul.program.domain.ProgramPhase@CLOSED",
    "area.filtersCellAttributes()",
    "condition.minPostId != null",
    "condition.keyword != null",
    "condition.area != null",
}
ONE_OF = {}


def test_false(qid, test):
    return test in FALSE_TESTS


def collection(qid, col):
    last = (col or "").split(".")[-1]
    if last in ("types", "spotTypes"):
        return ["'BAKJI'", "'CAMPSITE'"]
    if last in ("settings",):
        return ["'MOUNTAIN'"] if "area" in (col or "") or "nearby" in (col or "") else ["ROW"]
    if last == "domains":
        return ["'example.com'"]
    if last == "externalIds":
        return ["'LT-1'", "'LT-2'"]
    if last == "nights":
        return ["1", "2"]
    if last == "mailEventTypes":
        return ["'PROGRAM_APPLICATION_EXPIRED'"]
    if "values()" in (col or ""):
        return ["'NO_SHOW'"]
    if last in ("spots", "targets", "rows"):
        return ["ROW"]
    return [("1" if last.endswith("Ids") or last == "ids" else "1"), "2"]


def item_value(qid, k, rest, v):
    if v == "ROW" or rest.startswith("."):
        field = rest.lstrip(".").rstrip("}").split(",")[0].split(".")[-1]
        return value(qid, field, {"spotId": "1", "id": "1"}) or "NULL"
    return str(v)


def dollar(qid, name, props):
    if name == "type.name()":
        return "NO_SHOW"
    if name == "type.priority()":
        return "1"
    if name in props:
        return props[name]
    return {"table": "community_image", "parentColumn": "post_id", "thumbnailColumn": "thumbnail_key", "createdAt": "created_at", "attemptCount": "attempt_count", "condition": "1=1"}.get(name, name)
