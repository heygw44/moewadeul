#!/usr/bin/env python3
"""새 인덱스의 쓰기 비용. 같은 쓰기 2,000행을 인덱스 없이/있게 번갈아 트랜잭션 안에서 돌리고 되돌린다.
  writecost.py [회차=5]   결과: 표마다 쓰기 종류별 중앙값(ms)
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paths import D, S, ROOT, LEDGER  # noqa: E402,F401
import statistics, sys

from qtime import run
from ab import ddl

T = {
 "basecamp": (["ALTER TABLE basecamp ADD INDEX idx_basecamp_status_start_date (status, start_date), ADD INDEX idx_basecamp_contact_info (contact_info(1))"],
              ["ALTER TABLE basecamp DROP INDEX idx_basecamp_status_start_date, DROP INDEX idx_basecamp_contact_info"],
              {"insert": "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status, same_gender_only, contact_info, created_at, updated_at) SELECT leader_id, spot_id, title, description, start_date, end_date, capacity, status, same_gender_only, contact_info, created_at, updated_at FROM basecamp ORDER BY id DESC LIMIT 2000",
               "status update": "UPDATE basecamp SET status = 'CLOSED', closed_reason = 'LEADER' WHERE status = 'RECRUITING' ORDER BY id LIMIT 2000"}),
 "email_verification": (["ALTER TABLE email_verification ADD INDEX idx_email_verification_created_at (created_at)"], ["ALTER TABLE email_verification DROP INDEX idx_email_verification_created_at"],
              {"insert": "INSERT INTO email_verification (member_id, code_hash, request_ip, expires_at, created_at, updated_at) SELECT member_id, code_hash, request_ip, expires_at, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM email_verification ORDER BY id DESC LIMIT 2000"}),
 "password_reset_token": (["ALTER TABLE password_reset_token ADD INDEX idx_password_reset_token_expires_at (expires_at)"], ["ALTER TABLE password_reset_token DROP INDEX idx_password_reset_token_expires_at"],
              {"insert": "INSERT INTO password_reset_token (member_id, token_hash, expires_at, created_at, updated_at) SELECT member_id, SHA2(CONCAT(id, 'wc'), 256), UTC_TIMESTAMP(6) + INTERVAL 30 MINUTE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM password_reset_token ORDER BY id DESC LIMIT 2000"}),
 "identity_verification": (["ALTER TABLE identity_verification ADD INDEX idx_identity_verification_ci_retained_until (ci_retained_until)"], ["ALTER TABLE identity_verification DROP INDEX idx_identity_verification_ci_retained_until"],
              {"retain update": "UPDATE identity_verification SET ci_retained_until = UTC_TIMESTAMP(6) + INTERVAL 30 DAY WHERE ci_retained_until IS NULL ORDER BY id LIMIT 2000"}),
 "outbox_event": (["ALTER TABLE outbox_event ADD INDEX idx_outbox_event_status_published_at (status, published_at)"], ["ALTER TABLE outbox_event DROP INDEX idx_outbox_event_status_published_at"],
              {"insert": "INSERT INTO outbox_event (event_type, aggregate_type, aggregate_id, payload, status, attempt_count, created_at, updated_at) SELECT event_type, aggregate_type, aggregate_id, payload, 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM outbox_event ORDER BY id DESC LIMIT 2000",
               "publish update": "UPDATE outbox_event SET status = 'PUBLISHED', published_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6) WHERE status = 'PENDING' ORDER BY id LIMIT 2000"}),
 "sanction": (["ALTER TABLE sanction ADD INDEX idx_sanction_status_ends_at (status, ends_at)"], ["ALTER TABLE sanction DROP INDEX idx_sanction_status_ends_at"],
              {"insert": "INSERT INTO sanction (member_id, type, level, reason, starts_at, ends_at, status, created_at, updated_at) SELECT member_id, type, level, reason, starts_at, ends_at, status, created_at, updated_at FROM sanction ORDER BY id DESC LIMIT 2000",
               "expire update": "UPDATE sanction SET status = 'EXPIRED' WHERE status = 'ACTIVE' ORDER BY id LIMIT 2000"}),
 "community_comment": (["ALTER TABLE community_comment ADD INDEX idx_community_comment_status (status)"], ["ALTER TABLE community_comment DROP INDEX idx_community_comment_status"],
              {"insert": "INSERT INTO community_comment (post_id, member_id, parent_id, content, status, created_at, updated_at) SELECT post_id, member_id, NULL, content, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM community_comment WHERE parent_id IS NULL ORDER BY id DESC LIMIT 2000",
               "hide update": "UPDATE community_comment SET status = 'HIDDEN', updated_at = UTC_TIMESTAMP(6) WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 2000"}),
 "memory": (["ALTER TABLE memory ADD INDEX idx_memory_status (status)"], ["ALTER TABLE memory DROP INDEX idx_memory_status"],
              {"insert": "INSERT INTO memory (member_id, spot_id, latitude, longitude, visited_date, nights, content, visibility, created_at, updated_at, status) SELECT member_id, spot_id, latitude, longitude, visited_date, nights, content, visibility, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'ACTIVE' FROM memory ORDER BY id DESC LIMIT 2000",
               "edit update": "UPDATE memory SET content = CONCAT(content, '.'), updated_at = UTC_TIMESTAMP(6) WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 2000"}),
}

n = int(sys.argv[1]) if len(sys.argv) > 1 else 5
only = sys.argv[2:] 
for table, (add, drop, writes) in T.items():
    if only and table not in only:
        continue
    res = {k: {"없음": [], "있음": []} for k in writes}
    for i in range(n):
        for side in ("없음", "있음"):
            if side == "있음":
                ddl(add)
            for k, s in writes.items():
                # 인덱스를 막 만든 직후에는 새 인덱스 쪽이 버퍼 풀에 없어 첫 회만 크게 느리다. 첫 회는 버리고 두 번째 값을 쓴다.
                run(s, 1)
                res[k][side].append(run(s, 1)[0][0])
            if side == "있음":
                ddl(drop)
    for k, v in res.items():
        a, b = statistics.median(v["없음"]), statistics.median(v["있음"])
        print(f"{table} {k} 2,000행: 인덱스 없음 {a:.1f} ms → 있음 {b:.1f} ms ({(b - a) / a * 100:+.0f}%, {n}회 번갈아, 범위 {min(v['있음']):.1f}~{max(v['있음']):.1f})")
