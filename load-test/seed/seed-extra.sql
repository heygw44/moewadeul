-- 부하 검증 데이터의 두 번째 단계. 첫 단계(seed.sql)가 만든 장소와 검증 회원 위에 베이스캠프, 동행 기록, 신고·제재, 관리자 감사 로그,
-- 로그인 기록, 행사 신청, 커뮤니티, 추억 기록을 넣는다. 실행은 seed.sh가 맡고, 이 파일 앞에 규모 변수(@extra_member_count 등)를 SET으로 붙여 넘긴다.
--
-- 전제: 첫 단계를 마친 DB. 관리자 admin@loadtest.invalid가 있고, 아래 테이블이 비어 있어야 한다.
--   basecamp, member_report, sanction, community_post, memory
-- 하나라도 어긋나면 프로시저가 아무것도 넣지 않고 멈춘다. 운영 DB나 이미 이 단계를 돌린 DB에 잘못 돌려도 데이터가 섞이지 않게 하려는 안전장치다.
-- 로그인 기록, 행사, 관리자 감사 로그는 k6 스크립트(로그인, 행사 만들기)가 남길 수 있어서 비어 있는지 보지 않는다.
--
-- 난수: 행마다 같은 값을 여러 번 써야 하는 곳은 RAND() 대신 CRC32(소금 + 순번)로 만든 0~1 값을 쓴다. 같은 행에서 같은 소금이면 늘 같은 값이라
-- 조인 조건과 CASE의 여러 갈래에 써도 값이 바뀌지 않는다.
--
-- 회원 배정: 배경 회원 M명 중 앞의 100명은 동행을 많이 다닌 "많이 다닌 회원"으로 따로 둔다. 나머지는 (순번 × 7919 + 자리 × 104729) mod (M - 100)으로
-- 고른다. 두 수가 소수라서 한 베이스캠프 안의 자리끼리는 같은 회원이 나오지 않는다. 그래도 겹치면 INSERT IGNORE가 그 행만 버린다.
--
-- 생성 규칙은 load-test/README.md 1절에 같은 내용을 적는다.

DROP PROCEDURE IF EXISTS loadtest_seed_extra;

DELIMITER //

CREATE PROCEDURE loadtest_seed_extra(
    IN extra_member_count INT,
    IN basecamp_count INT,
    IN audit_log_count INT,
    IN login_history_count INT,
    IN program_count INT,
    IN post_count INT,
    IN memory_count INT)
BEGIN
    DECLARE chunk_size INT DEFAULT 100000;
    DECLARE hot_count INT DEFAULT 100;
    DECLARE admin_count INT DEFAULT 6;
    DECLARE done INT;
    DECLARE chunk_rows INT;
    DECLARE before_id BIGINT;
    DECLARE heavy_member BIGINT;
    DECLARE spot_n INT;
    DECLARE now_utc DATETIME(6) DEFAULT UTC_TIMESTAMP(6);
    DECLARE today_kst DATE DEFAULT DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR);

    IF (SELECT COUNT(*) FROM member WHERE email = 'admin@loadtest.invalid') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '첫 단계 검증 데이터가 없습니다. seed.sql을 먼저 돌려야 합니다.';
    END IF;
    IF (SELECT COUNT(*) FROM basecamp) > 0 OR (SELECT COUNT(*) FROM member_report) > 0
        OR (SELECT COUNT(*) FROM sanction) > 0 OR (SELECT COUNT(*) FROM community_post) > 0
        OR (SELECT COUNT(*) FROM memory) > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '두 번째 단계 테이블에 행이 있어 검증 데이터를 넣지 않았습니다.';
    END IF;
    IF extra_member_count < 10000 OR extra_member_count > 1000000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '배경 회원 수는 10000 이상 1000000 이하여야 합니다.';
    END IF;
    IF basecamp_count < 1000 OR post_count < 1000 OR memory_count < 1000 OR program_count < 1
        OR audit_log_count < 0 OR login_history_count < 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '규모 값이 너무 작습니다. README의 환경 변수 표를 확인해야 합니다.';
    END IF;

    SET SESSION cte_max_recursion_depth = 100000;
    DROP TEMPORARY TABLE IF EXISTS lt_seq;
    CREATE TEMPORARY TABLE lt_seq (n INT NOT NULL PRIMARY KEY);
    INSERT INTO lt_seq (n)
    WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < chunk_size - 1)
    SELECT n FROM seq;
    -- 한 베이스캠프·글 안의 자리 번호. 임시 테이블은 한 쿼리에서 두 번 참조할 수 없어서 lt_seq와 따로 둔다.
    DROP TEMPORARY TABLE IF EXISTS lt_k;
    CREATE TEMPORARY TABLE lt_k (n INT NOT NULL PRIMARY KEY);
    INSERT INTO lt_k (n) SELECT n FROM lt_seq WHERE n < 100;

    -- 활성 장소를 1부터 번호 매겨 둔다. 무작위 장소는 이 번호로 고른다.
    DROP TEMPORARY TABLE IF EXISTS lt_spot;
    CREATE TEMPORARY TABLE lt_spot (rn INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL, lat DOUBLE NOT NULL, lng DOUBLE NOT NULL);
    INSERT INTO lt_spot (rn, id, lat, lng)
    SELECT ROW_NUMBER() OVER (ORDER BY id), id, latitude, longitude FROM spot WHERE status = 'ACTIVE';
    SELECT COUNT(*) INTO spot_n FROM lt_spot;

    -- 1. 관리자 5명을 더 둔다(첫 단계의 관리자와 합쳐 6명). 감사 로그와 신고 처리자를 나눠 맡는다.
    INSERT INTO member (email, password_hash, password_changed_at, nickname, status, role, email_verified_at, created_at, updated_at)
    SELECT CONCAT('admin', n + 2, '@loadtest.invalid'), '$2y$10$O99wI2d3HP4syFLmbbnL4uGzyYNIeeEWqQNTBwQRT13s8UtRJBFZm', now_utc,
           CONCAT('ltadmin', n + 2), 'ACTIVE', 'ADMIN', now_utc, now_utc, now_utc
    FROM lt_k WHERE n < admin_count - 1 ORDER BY n;
    DROP TEMPORARY TABLE IF EXISTS lt_admin;
    CREATE TEMPORARY TABLE lt_admin (n INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL);
    INSERT INTO lt_admin (n, id)
    SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id FROM member WHERE role = 'ADMIN' AND email LIKE '%@loadtest.invalid';

    -- 2. 배경 회원. 동행, 신고, 글, 좋아요를 맡는다. k6가 로그인하는 user*@ 회원과 겹치지 않게 bg*@ 이메일을 쓴다.
    --    가입일은 2년 안에 흩뿌리고, 모두 본인확인을 마친 성인이다. 그래서 베이스캠프를 열 수 있는 신뢰 단계 1 이상이다.
    SET done = 0;
    WHILE done < extra_member_count DO
        SET chunk_rows = LEAST(chunk_size, extra_member_count - done);
        INSERT INTO member (email, password_hash, password_changed_at, nickname, status, role, self_age_group, self_gender,
                            email_verified_at, created_at, updated_at)
        SELECT CONCAT('bg', LPAD(done + n + 1, 7, '0'), '@loadtest.invalid'),
               '$2y$10$O99wI2d3HP4syFLmbbnL4uGzyYNIeeEWqQNTBwQRT13s8UtRJBFZm', now_utc - INTERVAL 730 DAY,
               CONCAT('bg', LPAD(done + n + 1, 7, '0')), 'ACTIVE', 'USER',
               ELT(1 + CRC32(CONCAT('age', done + n)) % 5, 'TWENTIES', 'THIRTIES', 'FORTIES', 'FIFTIES', 'SIXTIES_PLUS'),
               IF(CRC32(CONCAT('gen', done + n)) % 2 = 0, 'FEMALE', 'MALE'),
               now_utc - INTERVAL 730 DAY, now_utc - INTERVAL 730 DAY, now_utc - INTERVAL 730 DAY
        FROM lt_seq WHERE n < chunk_rows ORDER BY n;
        SET done = done + chunk_rows;
    END WHILE;
    DROP TEMPORARY TABLE IF EXISTS lt_bg;
    CREATE TEMPORARY TABLE lt_bg (idx INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL, email VARCHAR(254) NOT NULL);
    INSERT INTO lt_bg (idx, id, email)
    SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id, email FROM member WHERE email LIKE 'bg%@loadtest.invalid';
    INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at, created_at, updated_at)
    SELECT m.id, 1960 + CRC32(CONCAT('by', m.id)) % 46, m.self_gender, SHA2(CONCAT('loadtest-bg-', m.id), 256), 'FAKE',
           now_utc - INTERVAL 720 DAY, now_utc - INTERVAL 720 DAY, now_utc - INTERVAL 720 DAY
    FROM member m WHERE m.email LIKE 'bg%@loadtest.invalid';
    SELECT COUNT(*) AS background_members FROM lt_bg;

    -- 3. 베이스캠프. 상태는 완료 85%, 취소 8%, 모집 중 5%, 마감 1%, 확정 1%다.
    --    완료·취소는 출발일이 5~724일 전이라 끝난 날이 모두 어제 이전이다. 모집 중·마감은 2~60일 뒤, 확정은 오늘 출발이다.
    --    그래서 0시 자동 처리가 고를 행은 없다.
    --    1,000개에 하나(순번 끝자리 500)는 "붐비는 베이스캠프"다. 모집 중이고 정원 6, 멤버는 캠프 리더 한 명, 대기 신청 20건(상한), 거절 60건, 취소 10건이다.
    DROP TEMPORARY TABLE IF EXISTS lt_bc;
    CREATE TEMPORARY TABLE lt_bc (
        s INT NOT NULL PRIMARY KEY,
        basecamp_id BIGINT NULL,
        status VARCHAR(20) NOT NULL,
        hot BOOLEAN NOT NULL,
        capacity TINYINT NOT NULL,
        heads TINYINT NOT NULL,
        left_extra TINYINT NOT NULL,
        pend TINYINT NOT NULL,
        rej TINYINT NOT NULL,
        canc TINYINT NOT NULL,
        expi TINYINT NOT NULL,
        start_date DATE NOT NULL,
        nights TINYINT NOT NULL,
        spot_rn INT NOT NULL,
        created_at DATETIME(6) NOT NULL
    );
    SET done = 0;
    WHILE done < basecamp_count DO
        SET chunk_rows = LEAST(chunk_size, basecamp_count - done);
        INSERT INTO lt_bc (s, status, hot, capacity, heads, left_extra, pend, rej, canc, expi, start_date, nights, spot_rn, created_at)
        SELECT b.s, b.status, b.hot,
               IF(b.hot, 6, b.capacity),
               CASE
                   WHEN b.hot THEN 1
                   WHEN b.status IN ('COMPLETED', 'CONFIRMED') THEN 2 + FLOOR(b.u_heads * (b.capacity - 1))
                   WHEN b.status = 'RECRUITING' THEN 1 + FLOOR(b.u_heads * (b.capacity - 1))
                   WHEN b.status = 'CLOSED' THEN b.capacity
                   WHEN b.u_heads < 0.8 THEN 1
                   ELSE 1 + FLOOR(b.u_heads * (b.capacity - 1))
               END,
               IF(b.status = 'COMPLETED' AND b.s % 20 = 1, 1, 0),
               CASE WHEN b.hot THEN 20 WHEN b.status = 'RECRUITING' THEN FLOOR(b.u_app * 4) ELSE 0 END,
               IF(b.hot, 60, FLOOR(b.u_app * 3)),
               IF(b.hot, 10, FLOOR(b.u_app * 2)),
               IF(b.status IN ('COMPLETED', 'CONFIRMED', 'CANCELED'), FLOOR(b.u_app * 2), 0),
               b.start_date, b.nights,
               1 + FLOOR(b.u_spot * spot_n),
               CASE
                   WHEN b.status IN ('RECRUITING', 'CLOSED') THEN now_utc - INTERVAL (1 + FLOOR(b.u_app * 20)) DAY
                   ELSE TIMESTAMP(b.start_date) - INTERVAL (2 + FLOOR(b.u_app * 50)) DAY
               END
        FROM (
            SELECT done + n AS s,
                   CASE WHEN (done + n) % 1000 = 500 THEN 'RECRUITING' WHEN u < 0.85 THEN 'COMPLETED' WHEN u < 0.93 THEN 'CANCELED'
                        WHEN u < 0.98 THEN 'RECRUITING' WHEN u < 0.99 THEN 'CLOSED' ELSE 'CONFIRMED' END AS status,
                   (done + n) % 1000 = 500 AS hot,
                   2 + CRC32(CONCAT('cap', done + n)) % 5 AS capacity,
                   (CRC32(CONCAT('hd', done + n)) % 10000) / 10000 AS u_heads,
                   (CRC32(CONCAT('ap', done + n)) % 10000) / 10000 AS u_app,
                   (CRC32(CONCAT('sp', done + n)) % 1000003) / 1000003 AS u_spot,
                   1 + CRC32(CONCAT('nt', done + n)) % 3 AS nights,
                   CASE
                       WHEN (done + n) % 1000 = 500 OR u >= 0.93 AND u < 0.99
                           THEN today_kst + INTERVAL (2 + CRC32(CONCAT('sd', done + n)) % 59) DAY
                       WHEN u < 0.93 THEN today_kst - INTERVAL (5 + CRC32(CONCAT('sd', done + n)) % 720) DAY
                       ELSE today_kst
                   END AS start_date
            FROM (SELECT n, (CRC32(CONCAT('st', done + n)) % 10000) / 10000 AS u FROM lt_seq WHERE n < chunk_rows) q
        ) b;
        SET done = done + chunk_rows;
    END WHILE;

    -- 캠프 리더는 자리 0의 회원이다. 자리 0은 순번 × 7919 mod (M - 100)이라 회원마다 캠프를 많아야 두세 개 연다. 그래서 모집 중·마감 3개 제한을 넘지 않는다.
    SELECT COALESCE(MAX(id), 0) INTO before_id FROM basecamp;
    INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status, closed_reason,
                          min_trust_level, age_group_min, age_group_max, same_gender_only, required_gender, contact_info,
                          confirmed_at, completed_at, canceled_at, cancel_reason, created_at, updated_at)
    SELECT g.id, sp.id, CONCAT('검증 베이스캠프 ', LPAD(b.s + 1, 7, '0')), '같이 하룻밤 머물 동행을 찾습니다. 검증용 설명입니다.',
           b.start_date, b.start_date + INTERVAL b.nights DAY, b.capacity, b.status,
           IF(b.status = 'CLOSED', 'AUTO_FULL', NULL),
           CASE WHEN b.hot THEN NULL WHEN b.s % 10 < 6 THEN NULL WHEN b.s % 10 < 9 THEN 1 ELSE 2 END,
           IF(NOT b.hot AND b.s % 10 = 3, 20, NULL), IF(NOT b.hot AND b.s % 10 = 3, 40, NULL),
           NOT b.hot AND b.s % 20 = 7, IF(NOT b.hot AND b.s % 20 = 7, IF(b.s % 40 = 7, 'FEMALE', 'MALE'), NULL),
           IF(b.s % 3 = 0, CONCAT('https://open.kakao.com/o/lt', b.s), NULL),
           IF(b.status IN ('CONFIRMED', 'COMPLETED'), TIMESTAMP(b.start_date) - INTERVAL 9 HOUR - INTERVAL 1 DAY, NULL),
           IF(b.status = 'COMPLETED', TIMESTAMP(b.start_date + INTERVAL (b.nights + 1) DAY) - INTERVAL 9 HOUR, NULL),
           IF(b.status = 'CANCELED', TIMESTAMP(b.start_date) - INTERVAL 9 HOUR - INTERVAL 1 DAY, NULL),
           IF(b.status = 'CANCELED', IF(b.heads = 1, 'NOT_ENOUGH_MEMBERS', 'LEADER'), NULL),
           b.created_at, b.created_at
    FROM lt_bc b
    JOIN lt_spot sp ON sp.rn = b.spot_rn
    JOIN lt_bg g ON g.idx = hot_count + (b.s * 7919) % (extra_member_count - hot_count)
    ORDER BY b.s;
    -- AUTO_INCREMENT 값은 문장 사이에서 비지만, 한 문장 안에서는 넣은 순서대로 커진다. 그래서 id 순번이 곧 b.s다.
    DROP TEMPORARY TABLE IF EXISTS lt_bcid;
    CREATE TEMPORARY TABLE lt_bcid (s INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL);
    INSERT INTO lt_bcid (s, id) SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id FROM basecamp WHERE id > before_id;
    UPDATE lt_bc b JOIN lt_bcid i ON i.s = b.s SET b.basecamp_id = i.id;
    SELECT COUNT(*) AS basecamps FROM lt_bcid;

    -- 자리 k마다 회원과 역할을 정한다. 순서는 멤버(자리 0은 캠프 리더) → 먼저 떠난 멤버 → 대기 → 거절 → 본인 취소 → 만료 신청이다.
    -- 완료 50개에 하나는 자리 1에 "많이 다닌 회원"을 앉힌다. 그래서 그 100명은 완료 동행이 수십 번씩 쌓인다.
    DROP TEMPORARY TABLE IF EXISTS lt_slot;
    CREATE TEMPORARY TABLE lt_slot (
        s INT NOT NULL,
        k INT NOT NULL,
        kind VARCHAR(10) NOT NULL,
        member_id BIGINT NOT NULL,
        PRIMARY KEY (s, k)
    );
    INSERT INTO lt_slot (s, k, kind, member_id)
    SELECT x.s, x.k, x.kind, g.id
    FROM (
        SELECT b.s, k.n AS k,
               CASE
                   WHEN k.n < b.heads THEN 'ACTIVE'
                   WHEN k.n < b.heads + b.left_extra THEN 'LEFT'
                   WHEN k.n < b.heads + b.left_extra + b.pend THEN 'PENDING'
                   WHEN k.n < b.heads + b.left_extra + b.pend + b.rej THEN 'REJECTED'
                   WHEN k.n < b.heads + b.left_extra + b.pend + b.rej + b.canc THEN 'CANCELED'
                   ELSE 'EXPIRED'
               END AS kind,
               IF(b.status = 'COMPLETED' AND b.s % 50 = 0 AND k.n = 1 AND b.heads >= 2,
                  (b.s DIV 50) % hot_count,
                  hot_count + (b.s * 7919 + k.n * 104729) % (extra_member_count - hot_count)) AS idx
        FROM lt_bc b
        JOIN lt_k k ON k.n < b.heads + b.left_extra + b.pend + b.rej + b.canc + b.expi
    ) x
    JOIN lt_bg g ON g.idx = x.idx;

    INSERT IGNORE INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, left_at, early_leave, created_at, updated_at)
    SELECT b.basecamp_id, t.member_id, IF(t.k = 0, 'LEADER', 'MEMBER'), t.kind,
           b.created_at + INTERVAL t.k MINUTE,
           IF(t.kind = 'LEFT', TIMESTAMP(b.start_date) - INTERVAL 1 DAY, NULL),
           t.kind = 'LEFT', b.created_at + INTERVAL t.k MINUTE, b.created_at + INTERVAL t.k MINUTE
    FROM lt_slot t JOIN lt_bc b ON b.s = t.s
    WHERE t.kind IN ('ACTIVE', 'LEFT');

    INSERT IGNORE INTO basecamp_application (basecamp_id, applicant_id, message, status, decided_at, created_at, updated_at)
    SELECT b.basecamp_id, t.member_id, '함께 가고 싶어요.',
           CASE t.kind WHEN 'ACTIVE' THEN 'APPROVED' WHEN 'LEFT' THEN 'APPROVED' ELSE t.kind END,
           IF(t.kind = 'PENDING', NULL, b.created_at + INTERVAL t.k MINUTE),
           b.created_at + INTERVAL t.k MINUTE - INTERVAL 30 SECOND, b.created_at + INTERVAL t.k MINUTE
    FROM lt_slot t JOIN lt_bc b ON b.s = t.s
    WHERE t.k > 0;
    SELECT COUNT(*) AS basecamp_members FROM basecamp_member;

    -- 4. 동행 후기. 완료 베이스캠프의 멤버 쌍마다 절반 꼴로 쓴다. 쓴 시각은 완료 뒤 14일 안이고, 지금보다 늦지 않다.
    --    "다시 동행"은 90%이고, 태그는 그에 맞는 칭찬·아쉬움 태그를 하나, 40%는 하나 더 붙인다.
    INSERT INTO companion_review (basecamp_id, reviewer_id, reviewee_id, rejoin_wanted, comment, created_at)
    SELECT a.basecamp_id, a.member_id, c.member_id,
           CRC32(CONCAT('rj', a.id, ':', c.id)) % 10 < 9,
           IF(CRC32(CONCAT('cm', a.id, ':', c.id)) % 2 = 0, '시간 약속을 잘 지키고 뒷정리도 꼼꼼했어요.', NULL),
           GREATEST(bc.completed_at,
                    LEAST(bc.completed_at + INTERVAL (CRC32(CONCAT('rt', a.id, ':', c.id)) % (13 * 24)) HOUR, now_utc - INTERVAL 1 HOUR))
    FROM basecamp_member a
    JOIN basecamp_member c ON c.basecamp_id = a.basecamp_id AND c.member_id <> a.member_id AND c.status = 'ACTIVE'
    JOIN basecamp bc ON bc.id = a.basecamp_id AND bc.status = 'COMPLETED'
    WHERE a.status = 'ACTIVE' AND CRC32(CONCAT('rw', a.id, ':', c.id)) % 2 = 0;
    INSERT INTO companion_review_tag (review_id, tag)
    SELECT id, IF(rejoin_wanted, ELT(1 + id % 4, 'ON_TIME', 'LEAVE_NO_TRACE', 'CONSIDERATE', 'WELL_PREPARED'),
                  ELT(1 + id % 3, 'LATE', 'NO_SHOW', 'LITTERING'))
    FROM companion_review;
    INSERT IGNORE INTO companion_review_tag (review_id, tag)
    SELECT id, IF(rejoin_wanted, ELT(1 + (id + 1) % 4, 'ON_TIME', 'LEAVE_NO_TRACE', 'CONSIDERATE', 'WELL_PREPARED'),
                  ELT(1 + (id + 1) % 3, 'LATE', 'NO_SHOW', 'LITTERING'))
    FROM companion_review WHERE id % 5 < 2;
    SELECT COUNT(*) AS companion_reviews FROM companion_review;

    -- 5. 회원 신고와 제재. 완료 베이스캠프 다섯에 하나꼴로, 멤버 한 명이 캠프 리더를 신고한다.
    --    유형은 성희롱·위협 3%(급함), 금전 요구 10%, 나머지 네 유형을 고르게 둔다. 처리 상태는 조치 45%, 기각 40%, 접수 10%, 검토 중 5%다.
    INSERT INTO member_report (reporter_id, target_member_id, basecamp_id, kind, type, content, urgent, status,
                               handled_by, handled_at, result_note, created_at, updated_at)
    SELECT r.reporter_id, r.leader_id, r.basecamp_id, 'MEMBER', r.type, '검증용 신고 내용입니다. 약속 장소에 나타나지 않았어요.',
           r.type = 'HARASSMENT_OR_THREAT', r.status,
           IF(r.status IN ('ACTIONED', 'DISMISSED'), a.id, NULL),
           IF(r.status IN ('ACTIONED', 'DISMISSED'), LEAST(r.created_at + INTERVAL 2 DAY, now_utc - INTERVAL 30 MINUTE), NULL),
           IF(r.status IN ('ACTIONED', 'DISMISSED'), '검증용 처리 메모입니다.', NULL),
           r.created_at, r.created_at
    FROM (
        SELECT bc.id AS basecamp_id, bc.leader_id, m.reporter_id,
               LEAST(bc.completed_at + INTERVAL 1 HOUR, now_utc - INTERVAL 1 HOUR) AS created_at,
               CASE WHEN u_t < 0.03 THEN 'HARASSMENT_OR_THREAT' WHEN u_t < 0.13 THEN 'MONEY_REQUEST'
                    ELSE ELT(1 + bc.id % 4, 'NO_SHOW', 'OFFENSIVE_BEHAVIOR', 'FAKE_PROFILE', 'ILLEGAL_CAMPING_INDUCEMENT') END AS type,
               CASE WHEN u_s < 0.45 THEN 'ACTIONED' WHEN u_s < 0.85 THEN 'DISMISSED' WHEN u_s < 0.95 THEN 'RECEIVED'
                    ELSE 'IN_REVIEW' END AS status
        FROM (
            SELECT bc0.*, (CRC32(CONCAT('rt', bc0.id)) % 10000) / 10000 AS u_t, (CRC32(CONCAT('rs', bc0.id)) % 10000) / 10000 AS u_s
            FROM basecamp bc0 WHERE bc0.status = 'COMPLETED' AND bc0.id % 5 = 0
        ) bc
        JOIN (SELECT basecamp_id, MIN(member_id) AS reporter_id FROM basecamp_member
              WHERE role = 'MEMBER' AND status = 'ACTIVE' GROUP BY basecamp_id) m ON m.basecamp_id = bc.id
    ) r
    JOIN lt_admin a ON a.n = r.basecamp_id % admin_count;

    -- 조치한 신고마다 제재 하나. 경고 50%, 정지 7일 30%, 30일 15%, 영구 5%이고, 5%는 해제됐다. 기간이 지난 정지는 만료 상태로 둔다.
    -- 성희롱·위협 신고에는 접수 때 72시간 임시 정지가 먼저 붙는다(처리자 없음).
    DROP TEMPORARY TABLE IF EXISTS lt_sanction;
    CREATE TEMPORARY TABLE lt_sanction AS
    SELECT r.target_member_id AS member_id, r.id AS report_id, r.handled_by,
           r.handled_at AS starts_at,
           CASE WHEN u < 0.5 THEN 'WARNING' WHEN u < 0.8 THEN 'SUSPEND_7D' WHEN u < 0.95 THEN 'SUSPEND_30D' ELSE 'PERMANENT' END AS type,
           (CRC32(CONCAT('sl', r.id)) % 100) < 5 AS lifted
    FROM (SELECT r0.*, (CRC32(CONCAT('sk', r0.id)) % 10000) / 10000 AS u FROM member_report r0 WHERE r0.status = 'ACTIONED') r;
    INSERT INTO sanction (member_id, type, level, report_id, reason, starts_at, ends_at, status, created_by, lifted_by, lifted_at,
                          created_at, updated_at)
    SELECT x.member_id, x.type, ELT(FIELD(x.type, 'WARNING', 'SUSPEND_7D', 'SUSPEND_30D', 'PERMANENT'), 1, 2, 3, 4), x.report_id,
           '검증용 제재 사유입니다.', x.starts_at, x.ends_at,
           CASE WHEN x.lifted THEN 'LIFTED' WHEN x.ends_at IS NOT NULL AND x.ends_at <= now_utc THEN 'EXPIRED' ELSE 'ACTIVE' END,
           x.handled_by, IF(x.lifted, x.handled_by, NULL),
           IF(x.lifted, LEAST(x.starts_at + INTERVAL 1 HOUR, now_utc - INTERVAL 1 MINUTE), NULL),
           x.starts_at, x.starts_at
    FROM (
        SELECT s.*, CASE s.type WHEN 'SUSPEND_7D' THEN s.starts_at + INTERVAL 7 DAY
                                WHEN 'SUSPEND_30D' THEN s.starts_at + INTERVAL 30 DAY ELSE NULL END AS ends_at
        FROM lt_sanction s
    ) x;
    INSERT INTO sanction (member_id, type, level, report_id, reason, starts_at, ends_at, status, created_by, created_at, updated_at)
    SELECT r.target_member_id, 'TEMPORARY_72H', NULL, r.id,
           '성희롱·위협 신고가 접수되어 관리자가 확인하기 전까지 이용을 임시로 정지해요.',
           r.created_at, r.created_at + INTERVAL 72 HOUR,
           IF(r.created_at + INTERVAL 72 HOUR <= now_utc, 'EXPIRED', 'ACTIVE'), NULL, r.created_at, r.created_at
    FROM member_report r WHERE r.type = 'HARASSMENT_OR_THREAT';
    -- 활성 정지 제재가 있는 회원은 이용 정지 상태다. 영구 정지가 있으면 끝이 없고, 아니면 가장 늦게 끝나는 정지까지다.
    UPDATE member m
    JOIN (SELECT member_id, MAX(type = 'PERMANENT') AS permanent, MAX(ends_at) AS ends_at
          FROM sanction WHERE status = 'ACTIVE' AND type <> 'WARNING' GROUP BY member_id) x ON x.member_id = m.id
    SET m.status = 'SUSPENDED', m.suspended_until = IF(x.permanent, NULL, x.ends_at);
    SELECT COUNT(*) AS member_reports FROM member_report;
    SELECT COUNT(*) AS sanctions FROM sanction;

    -- 6. 관리자 감사 로그. 관리자 6명이 지난 1년 동안 남긴 조치를 고르게 흩뿌린다.
    SET done = 0;
    WHILE done < audit_log_count DO
        SET chunk_rows = LEAST(chunk_size, audit_log_count - done);
        INSERT INTO admin_audit_log (admin_id, action, target_type, target_id, detail, created_at)
        SELECT a.id, x.action,
               CASE
                   WHEN x.action LIKE 'REPORT%' THEN 'MEMBER_REPORT' WHEN x.action = 'SANCTION_LIFT' THEN 'SANCTION'
                   WHEN x.action LIKE 'SPOT%' THEN 'SPOT' WHEN x.action LIKE 'COMMUNITY%' THEN 'COMMUNITY_POST'
                   WHEN x.action LIKE 'MEMORY%' THEN 'MEMORY' WHEN x.action LIKE 'PROGRAM%' THEN 'PROGRAM' ELSE 'SYNC_JOB_RUN'
               END,
               1 + x.g % 100000, JSON_OBJECT('note', '검증용 감사 로그'),
               now_utc - INTERVAL (x.g % 525600) MINUTE
        FROM (
            SELECT done + n AS g,
                   ELT(1 + CRC32(CONCAT('aa', done + n)) % 14, 'REPORT_START_REVIEW', 'REPORT_ACTION', 'REPORT_DISMISS',
                       'SANCTION_LIFT', 'SPOT_HIDE', 'SPOT_RESTORE', 'COMMUNITY_HIDE', 'COMMUNITY_RESTORE', 'MEMORY_HIDE',
                       'MEMORY_RESTORE', 'SYNC_JOB_RUN', 'PROGRAM_CREATE', 'PROGRAM_UPDATE', 'PROGRAM_CANCEL') AS action
            FROM lt_seq WHERE n < chunk_rows
        ) x
        JOIN lt_admin a ON a.n = x.g % admin_count;
        SET done = done + chunk_rows;
    END WHILE;

    -- 7. 로그인 기록. 배경 회원 이메일로만 남겨서 k6가 쓰는 user*@ 회원의 로그인 잠금 판정에 끼지 않는다.
    --    98%는 최근 3개월 안(1시간 전보다 이전), 2%는 3~6개월 전이다. 그래서 하루 한 번 도는 정리 작업이 지울 행이 조금 있다. 성공 90%.
    SET done = 0;
    WHILE done < login_history_count DO
        SET chunk_rows = LEAST(chunk_size, login_history_count - done);
        INSERT INTO login_history (member_id, attempted_email, ip, success, created_at)
        SELECT g.id, g.email, CONCAT('10.', x.g % 250, '.', (x.g DIV 250) % 250, '.', 1 + x.g % 200),
               CRC32(CONCAT('ls', x.g)) % 10 < 9,
               IF(CRC32(CONCAT('lo', x.g)) % 100 < 98,
                  now_utc - INTERVAL 1 HOUR - INTERVAL (CRC32(CONCAT('lt', x.g)) % (88 * 24 * 60)) MINUTE,
                  now_utc - INTERVAL 91 DAY - INTERVAL (CRC32(CONCAT('lt', x.g)) % (90 * 24 * 60)) MINUTE)
        FROM (SELECT done + n AS g FROM lt_seq WHERE n < chunk_rows) x
        JOIN lt_bg g ON g.idx = (x.g * 7919) % extra_member_count;
        SET done = done + chunk_rows;
    END WHILE;
    SELECT COUNT(*) AS login_histories FROM login_history;

    -- 8. 공식 행사와 신청. 지난 2년 동안 열린 행사 program_count개에, 행사마다 50~299건씩 신청한다.
    --    확정 80%(결제 완료), 본인 취소 10%, 결제 만료 10%다. 확정 수는 정원을 넘지 않는다.
    --    앞으로 열릴 행사 하나에는 결제 대기 20건을 둔다. 결제 기한이 24시간 뒤라 1분마다 도는 만료 작업이 고를 행은 그동안 없다.
    SELECT COALESCE(MAX(id), 0) INTO before_id FROM program;
    INSERT INTO program (title, description, spot_id, location_text, start_at, end_at, capacity, fee, apply_open_at, apply_close_at,
                         payment_deadline_minutes, overnight, status, created_by, created_at, updated_at)
    SELECT CONCAT('검증 지난 행사 ', LPAD(n + 1, 5, '0')), '검증용 행사 설명입니다.', NULL, '검증용 장소',
           now_utc - INTERVAL (2 + n % 720) DAY, now_utc - INTERVAL (2 + n % 720) DAY + INTERVAL 1 DAY,
           300, 30000, now_utc - INTERVAL (40 + n % 720) DAY, now_utc - INTERVAL (3 + n % 720) DAY,
           15, n % 2 = 0, 'SCHEDULED', (SELECT MIN(id) FROM member WHERE role = 'ADMIN'),
           now_utc - INTERVAL (50 + n % 720) DAY, now_utc - INTERVAL (50 + n % 720) DAY
    FROM lt_seq WHERE n < program_count ORDER BY n;
    DROP TEMPORARY TABLE IF EXISTS lt_program;
    CREATE TEMPORARY TABLE lt_program (s INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL, apply_open_at DATETIME(6) NOT NULL,
                                       apps INT NOT NULL);
    INSERT INTO lt_program (s, id, apply_open_at, apps)
    SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id, apply_open_at, 50 + CRC32(CONCAT('pa', id)) % 250 FROM program WHERE id > before_id;
    INSERT IGNORE INTO program_application (program_id, member_id, status, payment_due_at, confirmed_at, canceled_at, cancel_reason,
                                            created_at, updated_at)
    SELECT x.id, g.id, x.status, x.created_at + INTERVAL 15 MINUTE,
           IF(x.status = 'CONFIRMED', x.created_at + INTERVAL 5 MINUTE, NULL),
           CASE x.status WHEN 'CANCELED' THEN x.created_at + INTERVAL 5 MINUTE WHEN 'EXPIRED' THEN x.created_at + INTERVAL 15 MINUTE END,
           CASE x.status WHEN 'CANCELED' THEN 'USER' WHEN 'EXPIRED' THEN 'EXPIRED' END,
           x.created_at, x.created_at
    FROM (
        SELECT p.s, p.id, k.n AS k, p.apply_open_at + INTERVAL k.n MINUTE AS created_at,
               CASE WHEN CRC32(CONCAT('ps', p.id, ':', k.n)) % 10 < 8 THEN 'CONFIRMED'
                    WHEN CRC32(CONCAT('ps', p.id, ':', k.n)) % 10 < 9 THEN 'CANCELED' ELSE 'EXPIRED' END AS status
        FROM lt_program p JOIN lt_seq k ON k.n < p.apps
    ) x
    JOIN lt_bg g ON g.idx = hot_count + (x.s * 7919 + x.k * 104729) % (extra_member_count - hot_count);
    INSERT INTO payment (program_application_id, amount, status, paid_at, created_at, updated_at)
    SELECT pa.id, 30000, 'PAID', pa.confirmed_at, pa.confirmed_at, pa.confirmed_at
    FROM program_application pa JOIN lt_program p ON p.id = pa.program_id
    WHERE pa.status = 'CONFIRMED';
    INSERT INTO program (title, description, spot_id, location_text, start_at, end_at, capacity, fee, apply_open_at, apply_close_at,
                         payment_deadline_minutes, overnight, status, created_by, created_at, updated_at)
    VALUES ('검증 다가오는 행사', '검증용 행사 설명입니다.', NULL, '검증용 장소', now_utc + INTERVAL 30 DAY,
            now_utc + INTERVAL 31 DAY, 100, 30000, now_utc - INTERVAL 1 DAY, now_utc + INTERVAL 20 DAY, 1440, TRUE, 'SCHEDULED',
            (SELECT MIN(id) FROM member WHERE role = 'ADMIN'), now_utc - INTERVAL 2 DAY, now_utc - INTERVAL 2 DAY);
    INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at, updated_at)
    SELECT LAST_INSERT_ID(), g.id, 'PENDING_PAYMENT', now_utc + INTERVAL 1440 MINUTE, now_utc, now_utc
    FROM lt_bg g WHERE g.idx BETWEEN hot_count AND hot_count + 19;
    SELECT COUNT(*) AS program_applications FROM program_application;

    -- 9. 커뮤니티 글. 순번이 클수록 최근 글이고, 2년 안에 고르게 흩뿌린다. 5%는 장소에 연결한다.
    --    상태는 활성 90%, 삭제 4%, 숨김 3%, 검토 대기 3%다. 다만 순번 30~35% 구간은 95%가 활성이 아니다. 그래서 최신순으로 훑는 목록이 그 구간에서 오래 읽는다.
    --    본문은 80%가 짧고(약 130~540자), 20%가 길다(약 1,900~4,900자). 모든 글에 "백패킹"이 들어가고, 1만 개에 두 개꼴로 희귀 낱말 "lt희귀토큰"이 들어간다.
    SET done = 0;
    WHILE done < post_count DO
        SET chunk_rows = LEAST(chunk_size, post_count - done);
        INSERT INTO community_post (member_id, title, content, spot_id, status, created_at, updated_at, view_count)
        SELECT g.id,
               CONCAT('검증 글 ', LPAD(x.g + 1, 7, '0'), ' ', ELT(1 + x.g % 5, '장비 추천', '박지 후기', '동행 모집 팁', '날씨 이야기', '초보 질문')),
               CONCAT(IF(x.g % 10000 IN (7, 5007), 'lt희귀토큰 ', ''),
                      REPEAT('백패킹 장비와 박지 이야기를 적은 검증용 본문입니다. ',
                             IF(CRC32(CONCAT('pl', x.g)) % 10 < 8, 5 + CRC32(CONCAT('pn', x.g)) % 16, 70 + CRC32(CONCAT('pn', x.g)) % 111))),
               IF(CRC32(CONCAT('pp', x.g)) % 100 < 5, sp.id, NULL),
               CASE
                   WHEN x.g >= post_count * 0.30 AND x.g < post_count * 0.35
                       THEN IF(CRC32(CONCAT('pb', x.g)) % 100 < 5, 'ACTIVE', 'HIDDEN')
                   WHEN CRC32(CONCAT('ps', x.g)) % 100 < 90 THEN 'ACTIVE'
                   WHEN CRC32(CONCAT('ps', x.g)) % 100 < 94 THEN 'DELETED'
                   WHEN CRC32(CONCAT('ps', x.g)) % 100 < 97 THEN 'HIDDEN'
                   ELSE 'PENDING_REVIEW'
               END,
               now_utc - INTERVAL ((post_count - x.g) * 63072000 DIV post_count) SECOND,
               now_utc - INTERVAL ((post_count - x.g) * 63072000 DIV post_count) SECOND,
               CRC32(CONCAT('pv', x.g)) % 500
        FROM (SELECT done + n AS g FROM lt_seq WHERE n < chunk_rows) x
        JOIN lt_bg g ON g.idx = (x.g * 7919) % extra_member_count
        JOIN lt_spot sp ON sp.rn = 1 + (x.g * 104729) % spot_n
        ORDER BY x.g;
        SET done = done + chunk_rows;
    END WHILE;
    DROP TEMPORARY TABLE IF EXISTS lt_post;
    CREATE TEMPORARY TABLE lt_post (s INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL, status VARCHAR(20) NOT NULL,
                                    created_at DATETIME(6) NOT NULL, hot BOOLEAN NOT NULL);
    INSERT INTO lt_post (s, id, status, created_at, hot)
    SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id, status, created_at, FALSE FROM community_post;
    -- "붐비는 글"은 가장 최근의 활성 글 다섯 개다. 댓글 수천 건과 좋아요 수천 건을 몰아준다.
    UPDATE lt_post SET hot = TRUE WHERE status = 'ACTIVE' ORDER BY s DESC LIMIT 5;
    SELECT COUNT(*) AS community_posts FROM lt_post;

    -- 댓글. 보통 글은 최상위 댓글 0~11건(대부분 0~2건), 붐비는 글은 최상위 3,500건과 답글 1,500건이다.
    -- 상태는 활성 92%, 삭제 5%(내용 없음), 숨김 2%, 검토 대기 1%다. 그래서 활성이 아닌 최상위 댓글에 활성 답글이 달린 경우도 생긴다.
    INSERT INTO community_comment (post_id, member_id, parent_id, content, status, created_at, updated_at)
    SELECT x.id, g.id, NULL,
           IF(x.u >= 92 AND x.u < 97, NULL, '검증용 댓글입니다. 좋은 정보 고마워요.'),
           CASE WHEN x.u < 92 THEN 'ACTIVE' WHEN x.u < 97 THEN 'DELETED' WHEN x.u < 99 THEN 'HIDDEN' ELSE 'PENDING_REVIEW' END,
           LEAST(x.created_at + INTERVAL (x.k + 1) MINUTE, now_utc), LEAST(x.created_at + INTERVAL (x.k + 1) MINUTE, now_utc)
    FROM (
        SELECT p.s, p.id, p.created_at, k.n AS k, CRC32(CONCAT('cs', p.id, ':', k.n)) % 100 AS u
        FROM lt_post p
        JOIN lt_seq k ON k.n < IF(p.hot, 3500, FLOOR(POW((CRC32(CONCAT('cc', p.id)) % 10000) / 10000, 3) * 12))
    ) x
    JOIN lt_bg g ON g.idx = (x.s * 7919 + x.k * 104729) % extra_member_count;
    DROP TEMPORARY TABLE IF EXISTS lt_hot_comment;
    CREATE TEMPORARY TABLE lt_hot_comment (post_id BIGINT NOT NULL, rn INT NOT NULL, id BIGINT NOT NULL, PRIMARY KEY (post_id, rn));
    INSERT INTO lt_hot_comment (post_id, rn, id)
    SELECT c.post_id, ROW_NUMBER() OVER (PARTITION BY c.post_id ORDER BY c.id) - 1, c.id
    FROM community_comment c JOIN lt_post p ON p.id = c.post_id AND p.hot;
    INSERT INTO community_comment (post_id, member_id, parent_id, content, status, created_at, updated_at)
    SELECT x.post_id, g.id, h.id,
           IF(x.u < 95, '검증용 답글입니다.', NULL), IF(x.u < 95, 'ACTIVE', 'DELETED'),
           now_utc - INTERVAL x.k MINUTE, now_utc - INTERVAL x.k MINUTE
    FROM (
        SELECT p.id AS post_id, k.n AS k, CRC32(CONCAT('rp', p.id, ':', k.n)) % 3500 AS parent_rn,
               CRC32(CONCAT('ru', p.id, ':', k.n)) % 100 AS u
        FROM lt_post p JOIN lt_seq k ON k.n < 1500 WHERE p.hot
    ) x
    JOIN lt_hot_comment h ON h.post_id = x.post_id AND h.rn = x.parent_rn
    JOIN lt_bg g ON g.idx = (x.k * 7919) % extra_member_count;
    SELECT COUNT(*) AS community_comments FROM community_comment;

    -- 좋아요. 60%는 0개, 25%는 1~2개, 10%는 3~5개, 5%는 5~304개다. 붐비는 글은 3,000개씩이다. 그래서 인기글(좋아요 5개 이상)은 일부뿐이다.
    INSERT IGNORE INTO community_post_like (post_id, member_id, created_at)
    SELECT x.id, g.id, LEAST(x.created_at + INTERVAL (x.k + 1) MINUTE, now_utc)
    FROM (
        SELECT p.s, p.id, p.created_at, k.n AS k
        FROM (
            SELECT p0.*, (CRC32(CONCAT('lk', p0.id)) % 10000) / 10000 AS u, (CRC32(CONCAT('lv', p0.id)) % 10000) / 10000 AS v
            FROM lt_post p0
        ) p
        JOIN lt_seq k ON k.n < CASE
            WHEN p.hot THEN 3000
            WHEN p.u < 0.60 THEN 0
            WHEN p.u < 0.85 THEN 1 + FLOOR(p.v * 2)
            WHEN p.u < 0.95 THEN 3 + FLOOR(p.v * 3)
            ELSE 5 + FLOOR(POW(p.v, 2) * 300)
        END
    ) x
    JOIN lt_bg g ON g.idx = (x.s * 7919 + x.k * 104729) % extra_member_count;
    SELECT COUNT(*) AS community_post_likes FROM community_post_like;

    -- 글 이미지. 열에 하나꼴로 1~5장을 붙인다. 붙지 않은 이미지는 정리 작업이 지우므로 만들지 않는다.
    INSERT INTO community_image (member_id, post_id, object_key, content_type, size_bytes, display_order, created_at)
    SELECT cp.member_id, cp.id, CONCAT('community/loadtest/', cp.id, '/', k.n, '.jpg'), 'image/jpeg', 350000, k.n, cp.created_at
    FROM community_post cp
    JOIN lt_k k ON k.n < 1 + CRC32(CONCAT('ci', cp.id)) % 5
    WHERE cp.id % 10 = 0;

    -- 10. 추억 기록. 첫 신청 회원(user000001)에게 기록 1,000개와 사진 3,000장(기록마다 3장)을 몰아준다(공개 40%).
    --     나머지 memory_count개는 배경 회원이 남긴다. 공개 75%, 상태는 활성 95%, 숨김 3%, 검토 대기 2%, 사진은 기록마다 0~5장이다.
    --     좌표는 무작위 활성 장소의 좌표이고, 절반은 그 장소에 연결한다.
    SELECT MIN(id) INTO heavy_member FROM member WHERE email = 'user000001@loadtest.invalid';
    SELECT COALESCE(MAX(id), 0) INTO before_id FROM memory;
    INSERT INTO memory (member_id, spot_id, latitude, longitude, visited_date, nights, content, visibility, created_at, updated_at, status)
    SELECT heavy_member, IF(n % 2 = 0, sp.id, NULL), sp.lat, sp.lng, today_kst - INTERVAL (n % 720) DAY, n % 4,
           '검증용 기록입니다. 새벽 안개가 좋았어요.', IF(n % 5 < 2, 'PUBLIC', 'PRIVATE'),
           now_utc - INTERVAL (n % 720) DAY, now_utc - INTERVAL (n % 720) DAY, IF(n % 50 = 0, 'HIDDEN', 'ACTIVE')
    FROM lt_seq JOIN lt_spot sp ON sp.rn = 1 + (n * 104729) % spot_n
    WHERE n < 1000 ORDER BY n;
    INSERT INTO memory_image (member_id, memory_id, object_key, content_type, size_bytes, display_order, created_at)
    SELECT m.member_id, m.id, CONCAT('memory/loadtest/', m.id, '/', k.n, '.jpg'), 'image/jpeg', 420000, k.n, m.created_at
    FROM memory m JOIN lt_k k ON k.n < 3
    WHERE m.id > before_id;
    SET done = 0;
    WHILE done < memory_count DO
        SET chunk_rows = LEAST(chunk_size, memory_count - done);
        INSERT INTO memory (member_id, spot_id, latitude, longitude, visited_date, nights, content, visibility, created_at, updated_at, status)
        SELECT g.id, IF(x.g % 2 = 0, sp.id, NULL), sp.lat, sp.lng, today_kst - INTERVAL (x.g % 720) DAY, x.g % 4,
               '검증용 기록입니다. 별이 많이 보였어요.',
               IF(CRC32(CONCAT('mv', x.g)) % 100 < 75, 'PUBLIC', 'PRIVATE'),
               now_utc - INTERVAL ((memory_count - x.g) * 63072000 DIV memory_count) SECOND,
               now_utc - INTERVAL ((memory_count - x.g) * 63072000 DIV memory_count) SECOND,
               CASE WHEN CRC32(CONCAT('ms', x.g)) % 100 < 95 THEN 'ACTIVE'
                    WHEN CRC32(CONCAT('ms', x.g)) % 100 < 98 THEN 'HIDDEN' ELSE 'PENDING_REVIEW' END
        FROM (SELECT done + n AS g FROM lt_seq WHERE n < chunk_rows) x
        JOIN lt_bg g ON g.idx = (x.g * 7919) % extra_member_count
        JOIN lt_spot sp ON sp.rn = 1 + (x.g * 104729) % spot_n
        ORDER BY x.g;
        SET done = done + chunk_rows;
    END WHILE;
    INSERT INTO memory_image (member_id, memory_id, object_key, content_type, size_bytes, display_order, created_at)
    SELECT m.member_id, m.id, CONCAT('memory/loadtest/', m.id, '/', k.n, '.jpg'), 'image/jpeg', 420000, k.n, m.created_at
    FROM memory m JOIN lt_k k ON k.n < CRC32(CONCAT('mi', m.id)) % 6
    WHERE m.member_id <> heavy_member;
    -- 기록 좋아요. 50%는 0개, 30%는 1~3개, 15%는 4~15개, 5%는 16~515개다.
    DROP TEMPORARY TABLE IF EXISTS lt_memory;
    CREATE TEMPORARY TABLE lt_memory (s INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL, likes INT NOT NULL);
    INSERT INTO lt_memory (s, id, created_at, likes)
    SELECT ROW_NUMBER() OVER (ORDER BY id) - 1, id, created_at,
           CASE WHEN u < 0.50 THEN 0 WHEN u < 0.80 THEN 1 + FLOOR(v * 3) WHEN u < 0.95 THEN 4 + FLOOR(v * 12)
                ELSE 16 + FLOOR(POW(v, 2) * 500) END
    FROM (SELECT id, created_at, (CRC32(CONCAT('ml', id)) % 10000) / 10000 AS u, (CRC32(CONCAT('mw', id)) % 10000) / 10000 AS v
          FROM memory WHERE visibility = 'PUBLIC') m;
    INSERT IGNORE INTO memory_like (memory_id, member_id, created_at)
    SELECT x.id, g.id, LEAST(x.created_at + INTERVAL (x.k + 1) MINUTE, now_utc)
    FROM (SELECT m.s, m.id, m.created_at, k.n AS k FROM lt_memory m JOIN lt_seq k ON k.n < m.likes) x
    JOIN lt_bg g ON g.idx = (x.s * 7919 + x.k * 104729) % extra_member_count;
    SELECT COUNT(*) AS memories FROM memory;
    SELECT COUNT(*) AS memory_likes FROM memory_like;

    DROP TEMPORARY TABLE IF EXISTS lt_memory, lt_hot_comment, lt_post, lt_program, lt_sanction, lt_slot, lt_bcid, lt_bc, lt_bg,
        lt_admin, lt_spot, lt_k, lt_seq;
END //

DELIMITER ;

CALL loadtest_seed_extra(@extra_member_count, @basecamp_count, @audit_log_count, @login_history_count, @program_count,
                         @post_count, @memory_count);
DROP PROCEDURE loadtest_seed_extra;

ANALYZE TABLE member, identity_verification, basecamp, basecamp_member, basecamp_application, companion_review,
    companion_review_tag, member_report, sanction, admin_audit_log, login_history, program, program_application, payment,
    community_post, community_comment, community_post_like, community_image, memory, memory_image, memory_like;
