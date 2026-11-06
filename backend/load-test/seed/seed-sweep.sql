-- 전체 API·쿼리 측정의 세 번째 단계. seed-extra.sql이 넣지 않는 표를 시험 규모로 채운다. 규모의 이유는 load-test/README.md 1절에 있다.
-- 정리 작업이 도는 표는 "매일 돌아온 뒤의 모습"으로 넣는다. 기한이 지난 행은 하루치만 남기고, 나머지는 기한 안의 행이다.
-- 전제: seed-extra.sql을 마친 DB. spot_review에 행이 있으면 아무것도 넣지 않고 멈춘다.

DROP PROCEDURE IF EXISTS loadtest_seed_sweep;

DELIMITER //

CREATE PROCEDURE loadtest_seed_sweep()
BEGIN
    DECLARE now_utc DATETIME(6) DEFAULT UTC_TIMESTAMP(6);
    DECLARE bg_count INT;
    DECLARE bakji_count INT;

    IF (SELECT COUNT(*) FROM community_post) = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'seed-extra.sql을 먼저 돌려야 합니다.';
    END IF;
    IF EXISTS (SELECT 1 FROM spot_review LIMIT 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '세 번째 단계를 이미 돌린 DB입니다.';
    END IF;

    SET SESSION group_concat_max_len = 1000000;

    DROP TEMPORARY TABLE IF EXISTS lt_seq, lt_bg, lt_spot;
    CREATE TEMPORARY TABLE lt_seq (n INT NOT NULL PRIMARY KEY);
    INSERT INTO lt_seq (n)
    SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + f.d * 100000
    FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
         (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
         (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c,
         (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d,
         (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e,
         (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f;

    CREATE TEMPORARY TABLE lt_bg (idx INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL);
    INSERT INTO lt_bg (idx, id)
    SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id FROM member WHERE email LIKE 'bg%@loadtest.invalid';
    SET bg_count = (SELECT COUNT(*) FROM lt_bg);

    CREATE TEMPORARY TABLE lt_spot (idx INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL);
    INSERT INTO lt_spot (idx, id)
    SELECT ROW_NUMBER() OVER (ORDER BY spot_id) - 1, spot_id FROM bakji_detail;
    SET bakji_count = (SELECT COUNT(*) FROM lt_spot);

    -- 앞선 k6 측정이 남긴 미발송 알림. 메일 작업이 측정 내내 이것을 보내면 측정과 무관한 쓰기가 섞인다.
    UPDATE notification SET email_status = 'SKIPPED', email_next_attempt_at = NULL WHERE email_status IN ('PENDING', 'FAILED');

    -- 알림 200만 건: 회원 20만 명 × 평균 10건. 지우는 작업이 없어 계속 자라는 표다. 첫 신청 회원에게 2만 건을 몰아 준다.
    INSERT INTO notification (member_id, type, title, body, link, dedup_key, read_at, email_status, email_sent_at, created_at)
    SELECT b.id,
           ELT(1 + CRC32(CONCAT('nt', s.n)) % 4, 'PROGRAM_APPLICATION_CONFIRMED', 'PROGRAM_APPLICATION_CANCELED', 'PROGRAM_APPLICATION_EXPIRED', 'PROGRAM_APPLICATION_CONFIRMED'),
           '측정용 알림', '측정용 알림 본문입니다.', '/programs/1', CONCAT('lt-sweep-a-', s.n),
           IF(CRC32(CONCAT('nr', s.n)) % 10 < 7, now_utc - INTERVAL s.n % 300 DAY, NULL),
           IF(CRC32(CONCAT('ns', s.n)) % 5 = 0, 'SKIPPED', 'SENT'),
           IF(CRC32(CONCAT('ns', s.n)) % 5 = 0, NULL, now_utc - INTERVAL 1 + s.n % 365 DAY),
           now_utc - INTERVAL s.n % 365 DAY
    FROM lt_seq s JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count
    WHERE s.n < 1000000;
    INSERT INTO notification (member_id, type, title, body, link, dedup_key, read_at, email_status, email_sent_at, created_at)
    SELECT b.id,
           ELT(1 + CRC32(CONCAT('nt2', s.n)) % 4, 'PROGRAM_APPLICATION_CONFIRMED', 'PROGRAM_APPLICATION_CANCELED', 'PROGRAM_APPLICATION_EXPIRED', 'PROGRAM_APPLICATION_CONFIRMED'),
           '측정용 알림', '측정용 알림 본문입니다.', '/programs/1', CONCAT('lt-sweep-b-', s.n),
           IF(CRC32(CONCAT('nr2', s.n)) % 10 < 7, now_utc - INTERVAL s.n % 300 DAY, NULL),
           IF(CRC32(CONCAT('ns2', s.n)) % 5 = 0, 'SKIPPED', 'SENT'),
           IF(CRC32(CONCAT('ns2', s.n)) % 5 = 0, NULL, now_utc - INTERVAL 1 + s.n % 365 DAY),
           now_utc - INTERVAL s.n % 365 DAY
    FROM lt_seq s JOIN lt_bg b ON b.idx = (s.n * 104729) % bg_count
    WHERE s.n < 980000;
    INSERT INTO notification (member_id, type, title, body, link, dedup_key, read_at, email_status, email_sent_at, created_at)
    SELECT m.id, 'PROGRAM_APPLICATION_CONFIRMED', '측정용 알림', '측정용 알림 본문입니다.', '/programs/1', CONCAT('lt-sweep-u-', s.n),
           IF(s.n % 10 < 7, now_utc - INTERVAL s.n % 300 DAY, NULL), 'SENT', now_utc - INTERVAL s.n % 365 DAY, now_utc - INTERVAL s.n % 365 DAY
    FROM lt_seq s JOIN member m ON m.email = 'user000001@loadtest.invalid'
    WHERE s.n < 20000;

    -- 아웃박스 50만 건: 7일 보관이라 하루 7만 건이 쌓이는 규모다. 보관 기한이 지난 하루치 2만 5천 건을 정리 작업이 지운다.
    INSERT INTO outbox_event (event_type, aggregate_type, aggregate_id, payload, status, attempt_count, published_at, created_at, updated_at)
    SELECT 'PROGRAM_APPLICATION_EXPIRED', 'PROGRAM_APPLICATION', s.n, JSON_OBJECT('memberId', 1, 'programId', 1, 'applicationId', s.n), 'PUBLISHED', 1,
           IF(s.n < 25000, now_utc - INTERVAL 8 DAY, now_utc - INTERVAL (s.n % 6) DAY),
           IF(s.n < 25000, now_utc - INTERVAL 8 DAY, now_utc - INTERVAL (s.n % 6) DAY),
           IF(s.n < 25000, now_utc - INTERVAL 8 DAY, now_utc - INTERVAL (s.n % 6) DAY)
    FROM lt_seq s WHERE s.n < 500000;

    -- 장소 후기 50만 건과 사진 20만 장: 장소 100만 곳 중 5%에 평균 10건. 붐비는 장소 하나에 5천 건을 둔다.
    INSERT IGNORE INTO spot_review (spot_id, member_id, visited_date, rating, content, created_at, updated_at)
    SELECT sp.id, b.id, DATE(now_utc - INTERVAL 1 + s.n % 700 DAY), 1 + CRC32(CONCAT('rt', s.n)) % 5, CONCAT('측정용 후기 ', s.n, ' 조용하고 물이 가까웠습니다.'),
           now_utc - INTERVAL s.n % 700 DAY, now_utc - INTERVAL s.n % 700 DAY
    FROM lt_seq s
    JOIN lt_spot sp ON sp.idx = (s.n % 50000) * 17
    JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count
    WHERE s.n < 495000;
    INSERT IGNORE INTO spot_review (spot_id, member_id, visited_date, rating, content, created_at, updated_at)
    SELECT sp.id, b.id, DATE(now_utc - INTERVAL 1 + s.n % 700 DAY), 1 + CRC32(CONCAT('rt', s.n)) % 5, CONCAT('붐비는 장소 후기 ', s.n),
           now_utc - INTERVAL s.n % 700 DAY, now_utc - INTERVAL s.n % 700 DAY
    FROM lt_seq s JOIN lt_spot sp ON sp.idx = 0 JOIN lt_bg b ON b.idx = (s.n * 104729) % bg_count
    WHERE s.n < 5000;
    INSERT INTO review_image (member_id, review_id, object_key, content_type, size_bytes, display_order, created_at, ever_attached)
    SELECT r.member_id, r.id, CONCAT('review/', r.member_id, '/lt-', r.id, '.jpg'), 'image/jpeg', 200000, 0, r.created_at, 1
    FROM spot_review r WHERE r.id % 5 < 2;
    -- 올리고 붙이지 않은 사진. 사진 정리 작업의 대상이다.
    INSERT INTO review_image (member_id, object_key, content_type, size_bytes, created_at)
    SELECT b.id, CONCAT('review/', b.id, '/lt-orphan-', s.n, '.jpg'), 'image/jpeg', 200000, now_utc - INTERVAL 3 DAY
    FROM lt_seq s JOIN lt_bg b ON b.idx = s.n WHERE s.n < 5000;
    INSERT INTO community_image (member_id, object_key, content_type, size_bytes, created_at)
    SELECT b.id, CONCAT('community/', b.id, '/lt-orphan-', s.n, '.jpg'), 'image/jpeg', 200000, now_utc - INTERVAL 3 DAY
    FROM lt_seq s JOIN lt_bg b ON b.idx = s.n WHERE s.n < 5000;
    INSERT INTO memory_image (member_id, object_key, content_type, size_bytes, created_at)
    SELECT b.id, CONCAT('memory/', b.id, '/lt-orphan-', s.n, '.jpg'), 'image/jpeg', 200000, now_utc - INTERVAL 3 DAY
    FROM lt_seq s JOIN lt_bg b ON b.idx = s.n WHERE s.n < 5000;

    -- 박지 확인 50만 건, 신고 10만 건. 신고 내용은 처리 뒤 1년 보관이라, 1년이 넘은 행은 이미 비워져 있고 하루치 1천 건만 남긴다.
    INSERT IGNORE INTO bakji_confirmation (spot_id, member_id, created_at)
    SELECT sp.id, b.id, now_utc - INTERVAL s.n % 700 DAY
    FROM lt_seq s JOIN lt_spot sp ON sp.idx = (s.n % 100000) * 8 JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count
    WHERE s.n < 500000;
    INSERT IGNORE INTO bakji_report (spot_id, reporter_id, reason, content, created_at, reviewed_at)
    SELECT sp.id, b.id, IF(s.n % 2 = 0, 'ILLEGAL_AREA', 'CLOSED'),
           CASE WHEN s.n % 10 < 4 AND s.n % 400 <> 0 THEN NULL ELSE '측정용 신고 내용' END,
           now_utc - INTERVAL 370 + s.n % 700 DAY,
           CASE WHEN s.n % 10 < 4 THEN now_utc - INTERVAL 366 + s.n % 2 DAY WHEN s.n % 10 < 7 THEN now_utc - INTERVAL s.n % 300 DAY END
    FROM lt_seq s JOIN lt_spot sp ON sp.idx = (s.n % 50000) * 16 JOIN lt_bg b ON b.idx = (s.n * 104729) % bg_count
    WHERE s.n < 100000;

    -- 커뮤니티 신고 20만 건(글·댓글), 기록 신고 5만 건. 관리자 목록이 신고 표를 묶어 읽으므로 글마다 고르지 않게 몰아 준다.
    INSERT IGNORE INTO community_report (target_type, target_id, reporter_id, reason, content, reviewed_at, created_at)
    SELECT 'POST', p.id, b.id, ELT(1 + s.n % 5, 'SPAM', 'ABUSE', 'ILLEGAL_CAMPING', 'PRIVACY', 'MONEY_SCAM'),
           CASE WHEN s.n % 10 < 4 AND s.n % 400 <> 0 THEN NULL ELSE '측정용 신고 내용' END,
           CASE WHEN s.n % 10 < 4 THEN now_utc - INTERVAL 366 + s.n % 2 DAY WHEN s.n % 10 < 7 THEN now_utc - INTERVAL s.n % 300 DAY END,
           now_utc - INTERVAL 370 + s.n % 700 DAY
    FROM lt_seq s
    JOIN (SELECT ROW_NUMBER() OVER (ORDER BY id) - 1 rn, id FROM community_post WHERE id % 10 = 0) p ON p.rn = (s.n * 31) % 20000
    JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count
    WHERE s.n < 120000;
    INSERT IGNORE INTO community_report (target_type, target_id, reporter_id, reason, content, reviewed_at, created_at)
    SELECT 'COMMENT', c.id, b.id, ELT(1 + s.n % 5, 'SPAM', 'ABUSE', 'ILLEGAL_CAMPING', 'PRIVACY', 'MONEY_SCAM'),
           CASE WHEN s.n % 10 < 4 AND s.n % 400 <> 0 THEN NULL ELSE '측정용 신고 내용' END,
           CASE WHEN s.n % 10 < 4 THEN now_utc - INTERVAL 366 + s.n % 2 DAY WHEN s.n % 10 < 7 THEN now_utc - INTERVAL s.n % 300 DAY END,
           now_utc - INTERVAL 370 + s.n % 700 DAY
    FROM lt_seq s
    JOIN (SELECT ROW_NUMBER() OVER (ORDER BY id) - 1 rn, id FROM community_comment WHERE id % 10 = 0 AND status = 'ACTIVE' LIMIT 40000) c ON c.rn = (s.n * 31) % 40000
    JOIN lt_bg b ON b.idx = (s.n * 104729) % bg_count
    WHERE s.n < 80000;
    INSERT IGNORE INTO memory_report (memory_id, reporter_id, reason, content, reviewed_at, created_at)
    SELECT m.id, b.id, ELT(1 + s.n % 5, 'SPAM', 'ABUSE', 'ILLEGAL_CAMPING', 'PRIVACY', 'MONEY_SCAM'),
           CASE WHEN s.n % 10 < 4 AND s.n % 400 <> 0 THEN NULL ELSE '측정용 신고 내용' END,
           CASE WHEN s.n % 10 < 4 THEN now_utc - INTERVAL 366 + s.n % 2 DAY WHEN s.n % 10 < 7 THEN now_utc - INTERVAL s.n % 300 DAY END,
           now_utc - INTERVAL 370 + s.n % 700 DAY
    FROM lt_seq s
    JOIN (SELECT ROW_NUMBER() OVER (ORDER BY id) - 1 rn, id FROM memory WHERE id % 10 = 0) m ON m.rn = (s.n * 31) % 10000
    JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count
    WHERE s.n < 50000;

    -- 혼자 가는 일정 30만 건, 빈자리 알림 10만 건(다가오는 행사에 5천 건을 몰아 준다), 알림 설정 40만 건.
    INSERT INTO solo_trip (member_id, spot_id, start_date, end_date, created_at)
    SELECT b.id, sp.id, DATE(now_utc - INTERVAL 300 DAY) + INTERVAL s.n % 400 DAY, DATE(now_utc - INTERVAL 300 DAY) + INTERVAL (s.n % 400) + 2 DAY, now_utc - INTERVAL s.n % 300 DAY
    FROM lt_seq s JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count JOIN lt_spot sp ON sp.idx = (s.n * 31) % bakji_count
    WHERE s.n < 300000;
    INSERT IGNORE INTO program_vacancy_alert (program_id, member_id, notified_at, created_at)
    SELECT p.id, b.id, IF(s.n % 3 = 0, now_utc - INTERVAL s.n % 100 DAY, NULL), now_utc - INTERVAL s.n % 100 DAY
    FROM lt_seq s
    JOIN (SELECT ROW_NUMBER() OVER (ORDER BY id) - 1 rn, id FROM program) p ON p.rn = s.n % (SELECT COUNT(*) FROM program)
    JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count
    WHERE s.n < 95000;
    INSERT IGNORE INTO program_vacancy_alert (program_id, member_id, created_at)
    SELECT (SELECT MAX(id) FROM program), b.id, now_utc FROM lt_seq s JOIN lt_bg b ON b.idx = s.n * 3 WHERE s.n < 5000;
    INSERT IGNORE INTO notification_setting (member_id, type, email_enabled)
    SELECT b.id, ELT(1 + s.n % 2, 'PROGRAM_APPLICATION_CONFIRMED', 'PROGRAM_APPLICATION_CANCELED'), s.n % 3 = 0
    FROM lt_seq s JOIN lt_bg b ON b.idx = FLOOR(s.n / 2)
    WHERE s.n < 400000;

    -- 인증과 재설정 기록: 코드는 7일 보관이라 가입과 재발송을 합쳐 40만 건, 재설정 토큰 10만 건. 기한이 지난 행은 하루치만 둔다.
    INSERT INTO email_verification (member_id, code_hash, request_ip, expires_at, verified_at, created_at, updated_at)
    SELECT b.id, SHA2(CONCAT(b.id, ':000000'), 256), CONCAT('10.', s.n % 200, '.', s.n % 250, '.1'),
           now_utc - INTERVAL s.n % 7 DAY + INTERVAL 10 MINUTE,
           IF(s.n % 5 = 0, NULL, now_utc - INTERVAL s.n % 7 DAY),
           IF(s.n < 20000, now_utc - INTERVAL 8 DAY, now_utc - INTERVAL s.n % 7 DAY), now_utc - INTERVAL s.n % 7 DAY
    FROM lt_seq s JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count
    WHERE s.n < 400000;
    INSERT INTO password_reset_token (member_id, token_hash, expires_at, used_at, created_at, updated_at)
    SELECT b.id, SHA2(CONCAT('lt-old-', s.n), 256),
           IF(s.n < 5000, now_utc - INTERVAL 3 DAY, now_utc - INTERVAL 1 HOUR),
           IF(s.n % 3 = 0, now_utc - INTERVAL 1 HOUR, NULL),
           now_utc - INTERVAL 3 DAY, now_utc - INTERVAL 1 HOUR
    FROM lt_seq s JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count
    WHERE s.n < 100000;
    INSERT INTO password_reset_throttle (throttle_key, window_start, request_count, last_request_at, created_at, updated_at)
    SELECT SHA2(CONCAT('lt-throttle-', s.n), 256), now_utc - INTERVAL 1 HOUR, 1, now_utc - INTERVAL IF(s.n < 5000, 48, 1) HOUR, now_utc - INTERVAL 2 DAY, now_utc - INTERVAL 1 HOUR
    FROM lt_seq s WHERE s.n < 100000;
    INSERT INTO email_verification_ip_lock (ip_key, last_requested_at)
    SELECT SHA2(CONCAT('lt-ip-', s.n), 256), now_utc - INTERVAL IF(s.n < 5000, 48, 1) HOUR FROM lt_seq s WHERE s.n < 50000;
    INSERT INTO login_attempt_lock (attempt_key, last_attempt_at)
    SELECT SHA2(CONCAT('lt-attempt-', s.n), 256), now_utc - INTERVAL IF(s.n < 5000, 48, 1) HOUR FROM lt_seq s WHERE s.n < 200000;
    INSERT INTO signup_duplicate_attempt (ip_key, attempted_at)
    SELECT SHA2(CONCAT('lt-ip-', s.n % 5000), 256), now_utc - INTERVAL IF(s.n < 5000, 8, s.n % 6) DAY FROM lt_seq s WHERE s.n < 50000;
    INSERT IGNORE INTO ai_search_usage (member_id, usage_date, request_count, input_tokens, output_tokens, updated_at)
    SELECT b.id, DATE(now_utc - INTERVAL s.n % 91 DAY), 1 + s.n % 5, 900, 40, now_utc
    FROM lt_seq s JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count WHERE s.n < 200000;
    INSERT IGNORE INTO idempotency_record (member_id, idempotency_key, request_hash, response_status, response_body, created_at)
    SELECT b.id, CONCAT('lt-', s.n), SHA2(s.n, 256), 201, JSON_OBJECT('id', s.n), IF(s.n < 30000, now_utc - INTERVAL 2 DAY, now_utc - INTERVAL s.n % 86000 SECOND)
    FROM lt_seq s JOIN lt_bg b ON b.idx = (s.n * 7919) % bg_count WHERE s.n < 500000;
    INSERT INTO sync_job_run (job_type, status, processed_count, skipped_count, started_at, finished_at, created_at, updated_at)
    SELECT ELT(1 + s.n % 4, 'GOCAMPING', 'FOREST', 'FOREST_STANDARD', 'PARK_BOUNDARY'), 'COMPLETED', 4000, 0, now_utc - INTERVAL s.n DAY, now_utc - INTERVAL s.n DAY, now_utc - INTERVAL s.n DAY, now_utc - INTERVAL s.n DAY
    FROM lt_seq s WHERE s.n < 1000;
    INSERT IGNORE INTO public_spot_setting (spot_id, setting)
    SELECT spot_id, ELT(1 + spot_id % 7, 'MOUNTAIN', 'FOREST', 'VALLEY', 'BEACH', 'ISLAND', 'RIVER', 'LAKE') FROM public_spot_detail;
    INSERT IGNORE INTO public_spot_setting (spot_id, setting)
    SELECT spot_id, ELT(1 + (spot_id + 3) % 7, 'MOUNTAIN', 'FOREST', 'VALLEY', 'BEACH', 'ISLAND', 'RIVER', 'LAKE') FROM public_spot_detail;

    -- 공원 경계 100곳: 국립·도립·군립공원 실제 수와 비슷하다. 실제 경계처럼 꼭짓점 500개짜리 원으로 만든다.
    CREATE TEMPORARY TABLE lt_vertex (n INT NOT NULL PRIMARY KEY);
    INSERT INTO lt_vertex (n) SELECT n FROM lt_seq WHERE n <= 500;
    INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)
    SELECT CONCAT('측정용 공원 ', a.n), ELT(1 + a.n % 3, 'NATIONAL_PARK', 'PROVINCIAL_PARK', 'COUNTY_PARK'), 'KDPA', DATE(now_utc),
           ST_GeomFromText(CONCAT('MULTIPOLYGON(((',
               (SELECT GROUP_CONCAT(CONCAT(127.6 + (a.n % 10) * 0.25 - 1.2 + 0.1 * COS(2 * PI() * v.n / 500), ' ', 37.4 - FLOOR(a.n / 10) * 0.25 + 0.1 * SIN(2 * PI() * v.n / 500)) ORDER BY v.n SEPARATOR ',')
                FROM lt_vertex v),
               ')))'), 4326, 'axis-order=long-lat'),
           now_utc, now_utc
    FROM (SELECT n FROM lt_seq WHERE n < 100) a;

    -- 탈퇴 회원 1만 명. CI는 30일 보관이라 하루치 300명만 기한을 넘겼다. 작업이 지우는 행이 있어야 쿼리를 잰 것이 된다.
    INSERT INTO member (email, password_hash, password_changed_at, nickname, status, role, withdrawn_at, created_at, updated_at)
    SELECT CONCAT('withdrawn-', s.n, '@loadtest.invalid'), 'x', now_utc, CONCAT('탈퇴', s.n), 'WITHDRAWN', 'USER', now_utc - INTERVAL s.n % 31 DAY, now_utc - INTERVAL 400 DAY, now_utc
    FROM lt_seq s WHERE s.n < 10000;
    INSERT INTO identity_verification (member_id, ci_hash, provider, verified_at, ci_retained_until, created_at, updated_at)
    SELECT m.id, SHA2(CONCAT('lt-ci-w-', m.id), 256), 'FAKE', now_utc - INTERVAL 400 DAY,
           m.withdrawn_at + INTERVAL 30 DAY, now_utc - INTERVAL 400 DAY, now_utc
    FROM member m WHERE m.status = 'WITHDRAWN';

    -- 가입하고 인증하지 않은 회원. 1,000명은 7일이 지나 정리 대상이고, 1,000명은 코드 123456으로 인증하는 API의 재료다.
    INSERT INTO member (email, password_hash, password_changed_at, nickname, status, role, created_at, updated_at)
    SELECT CONCAT('unv-old-', s.n, '@loadtest.invalid'), (SELECT password_hash FROM member WHERE email = 'bg0000001@loadtest.invalid'), now_utc, CONCAT('uo', s.n), 'UNVERIFIED', 'USER', now_utc - INTERVAL 8 DAY, now_utc - INTERVAL 8 DAY
    FROM lt_seq s WHERE s.n < 1000;
    INSERT INTO member (email, password_hash, password_changed_at, nickname, status, role, created_at, updated_at)
    SELECT CONCAT('unv-new-', LPAD(s.n, 5, '0'), '@loadtest.invalid'), (SELECT password_hash FROM member WHERE email = 'bg0000001@loadtest.invalid'), now_utc, CONCAT('un', s.n), 'UNVERIFIED', 'USER', now_utc - INTERVAL 1 HOUR, now_utc - INTERVAL 1 HOUR
    FROM lt_seq s WHERE s.n < 1000;
    INSERT INTO email_verification (member_id, code_hash, request_ip, expires_at, created_at, updated_at)
    SELECT m.id, SHA2(CONCAT(m.id, ':123456'), 256), '10.9.9.9', now_utc + INTERVAL 7 DAY, now_utc - INTERVAL 1 HOUR, now_utc - INTERVAL 1 HOUR
    FROM member m WHERE m.email LIKE 'unv-new-%';

    -- 재설정 확인 API의 재료. 토큰 원문은 측정 도구가 같은 규칙으로 만든다.
    INSERT INTO password_reset_token (member_id, token_hash, expires_at, created_at, updated_at)
    SELECT b.id, SHA2(RPAD(CONCAT('ltreset', s.n), 43, 'x'), 256), now_utc + INTERVAL 7 DAY, now_utc, now_utc
    FROM lt_seq s JOIN lt_bg b ON b.idx = 150000 + s.n WHERE s.n < 1000;

    -- 연락처는 진행 중인 캠프와, 완료 뒤 7일이 안 된 캠프만 가진다. 정리 작업이 매일 돈 뒤의 모습이다. 지운 대상 300건을 하루치로 남긴다.
    UPDATE basecamp SET contact_info = NULL;
    UPDATE basecamp SET contact_info = 'open.kakao.com/o/lt' WHERE status IN ('RECRUITING', 'CLOSED', 'CONFIRMED');
    UPDATE basecamp SET contact_info = 'open.kakao.com/o/lt' WHERE status = 'COMPLETED' AND id % 1000 = 0;
    UPDATE basecamp SET contact_info = 'open.kakao.com/o/lt' WHERE status = 'CANCELED' AND id % 100 = 0;

    DROP TEMPORARY TABLE IF EXISTS lt_seq, lt_bg, lt_spot, lt_vertex;
END //

DELIMITER ;

SET SESSION sql_log_bin = 0;
CALL loadtest_seed_sweep();
DROP PROCEDURE loadtest_seed_sweep;
