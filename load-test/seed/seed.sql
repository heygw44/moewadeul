-- 출시 전 부하 검증에 쓸 데이터를 만든다. 운영 데이터를 복사하지 않고, 이 스크립트가 규칙에 따라 가짜 데이터를 만든다.
-- 실행은 seed.sh가 맡는다. seed.sh는 이 파일 앞에 @spot_count(장소 수)와 @member_count(신청 회원 수)를 SET으로 붙여 넘긴다.
--
-- 전제: 앱을 한 번 띄워 Flyway가 스키마를 만든 빈 DB. spot이나 member에 행이 하나라도 있으면 프로시저가 아무것도 넣지 않고 멈춘다.
-- 운영 DB에 잘못 돌려도 데이터가 섞이지 않게 하려는 안전장치다.
--
-- 생성 규칙(load-test/README.md에도 같은 내용을 적는다)
-- 1. 회원
--    - 관리자 1명: admin@loadtest.invalid. k6 스크립트가 행사를 만들 때 쓴다.
--    - 신청 회원 @member_count명: user000001@loadtest.invalid부터. 이메일 인증을 마친 활성 회원이다.
--    - 비밀번호는 모두 같은 검증용 가짜 값 'LoadTest#2026'이고, 그 BCrypt(강도 10) 해시를 넣는다. 운영에는 이 회원이 없다.
-- 2. 본인확인: 신청 회원마다 가짜 제공자 본인확인 행을 넣는다. 출생연도는 1970~2000년이라 모두 성인이다.
--    그래서 신청 회원은 숙박 행사에 신청할 수 있는 신뢰 단계 1이 된다.
-- 3. 장소 @spot_count건
--    - 유형: 박지 90%, 야영장 8%, 휴양림 2%.
--    - 상태: 숨김과 검토 대기는 박지에만 둔다. 박지의 약 1.1%가 검토 대기, 약 2.2%가 숨김이라 전체로는 활성 97%, 검토 대기 1%, 숨김 2%다.
--    - 공원 경계 경고: 박지의 10%. 경고 플래그만 켜고 공원 경계 행은 연결하지 않는다. 지도 조회는 플래그만 읽기 때문이다.
--    - 휴장: 야영장·휴양림의 5%를 임시 휴장(기간 없음)으로 둔다. 기간이 없으면 날짜와 상관없이 휴장으로 판정되므로 측정 날짜에 따라 비율이 바뀌지 않는다.
--    - 좌표: 시·도 17곳의 중심 좌표에서 가중치에 따라 한 곳을 고르고, 정규분포(박스-뮬러 변환)로 흩뿌린다.
--      가중치는 공공 야영장의 시·도별 분포를 닮게 잡은 근사값이고, 표준편차는 도 0.35도, 광역시 0.08도, 제주 0.15도다.
--      결과는 위도 33.0~38.6, 경도 124.6~131.9 안으로 자른다. 그래서 일부 점은 바다나 북한 지역에 떨어질 수 있다.
--    - 기상청 격자(weather_nx, weather_ny): 위경도를 선형 근사한 값을 1~149, 1~253 안으로 자른다. 검증 중에는 날씨를 부르지 않아서 정확한 격자가 아니다.
-- 4. 상세 행: 야영장·휴양림마다 공공 장소 상세(외부 ID 'LT-장소ID'), 박지마다 박지 상세(제보자는 첫 신청 회원, 물·화장실 각 50%).
--
-- 장소는 10만 건씩 나눠 넣는다. 한 번에 넣으면 언두 로그가 커지기 때문이다.

DROP PROCEDURE IF EXISTS loadtest_seed;

DELIMITER //

CREATE PROCEDURE loadtest_seed(IN spot_count INT, IN member_count INT)
BEGIN
    DECLARE chunk_size INT DEFAULT 100000;
    DECLARE inserted INT DEFAULT 0;
    DECLARE chunk_rows INT;
    DECLARE before_id BIGINT;
    DECLARE reporter BIGINT;
    DECLARE now_utc DATETIME(6) DEFAULT UTC_TIMESTAMP(6);

    IF (SELECT COUNT(*) FROM spot) > 0 OR (SELECT COUNT(*) FROM member) > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '빈 DB가 아닙니다. spot이나 member에 행이 있어 검증 데이터를 넣지 않았습니다.';
    END IF;
    IF member_count < 1 OR member_count > chunk_size THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '신청 회원 수는 1 이상 100000 이하여야 합니다.';
    END IF;
    IF spot_count < 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '장소 수는 1 이상이어야 합니다.';
    END IF;

    -- 0부터 99,999까지의 순번. 재귀 CTE로 만든다. 임시 테이블은 한 쿼리에서 두 번 참조할 수 없어서 교차 조인을 쓰지 않는다.
    SET SESSION cte_max_recursion_depth = 100000;
    DROP TEMPORARY TABLE IF EXISTS loadtest_seq;
    CREATE TEMPORARY TABLE loadtest_seq (n INT NOT NULL PRIMARY KEY);
    INSERT INTO loadtest_seq (n)
    WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < chunk_size - 1)
    SELECT n FROM seq;

    -- 시·도 중심 좌표와 가중치(합계 100), 흩뿌리는 표준편차(도). cum_lo 이상 cum_hi 미만의 난수가 그 시·도를 고른다.
    DROP TEMPORARY TABLE IF EXISTS loadtest_region;
    CREATE TEMPORARY TABLE loadtest_region (
        name VARCHAR(10) NOT NULL,
        lat DOUBLE NOT NULL,
        lng DOUBLE NOT NULL,
        sigma DOUBLE NOT NULL,
        weight INT NOT NULL,
        cum_lo INT NULL,
        cum_hi INT NULL
    );
    INSERT INTO loadtest_region (name, lat, lng, sigma, weight) VALUES
        ('경기', 37.55, 127.25, 0.35, 22),
        ('강원', 37.75, 128.30, 0.35, 17),
        ('경북', 36.35, 128.75, 0.35, 9),
        ('경남', 35.35, 128.25, 0.35, 8),
        ('충남', 36.55, 126.85, 0.35, 7),
        ('충북', 36.75, 127.75, 0.35, 6),
        ('전남', 34.85, 126.95, 0.35, 6),
        ('전북', 35.72, 127.15, 0.35, 5),
        ('인천', 37.46, 126.44, 0.08, 4),
        ('제주', 33.38, 126.55, 0.15, 3),
        ('서울', 37.56, 126.98, 0.08, 2),
        ('부산', 35.18, 129.08, 0.08, 2),
        ('대구', 35.87, 128.60, 0.08, 2),
        ('대전', 36.35, 127.38, 0.08, 2),
        ('광주', 35.16, 126.85, 0.08, 2),
        ('울산', 35.54, 129.31, 0.08, 2),
        ('세종', 36.48, 127.29, 0.08, 1);
    -- 임시 테이블은 한 쿼리에서 두 번 참조할 수 없어서 누적 합은 변수로 계산한다.
    SET @loadtest_cum = 0;
    UPDATE loadtest_region
    SET cum_lo = @loadtest_cum, cum_hi = (@loadtest_cum := @loadtest_cum + weight)
    ORDER BY weight DESC, name;

    -- 회원. 관리자를 먼저 넣고, 신청 회원을 이어서 넣는다.
    INSERT INTO member (email, password_hash, password_changed_at, nickname, status, role,
                        email_verified_at, created_at, updated_at)
    VALUES ('admin@loadtest.invalid', '$2y$10$O99wI2d3HP4syFLmbbnL4uGzyYNIeeEWqQNTBwQRT13s8UtRJBFZm', now_utc,
            'ltadmin', 'ACTIVE', 'ADMIN', now_utc, now_utc, now_utc);
    INSERT INTO member (email, password_hash, password_changed_at, nickname, status, role,
                        email_verified_at, created_at, updated_at)
    SELECT CONCAT('user', LPAD(n + 1, 6, '0'), '@loadtest.invalid'),
           '$2y$10$O99wI2d3HP4syFLmbbnL4uGzyYNIeeEWqQNTBwQRT13s8UtRJBFZm', now_utc,
           CONCAT('lt', LPAD(n + 1, 6, '0')), 'ACTIVE', 'USER', now_utc, now_utc, now_utc
    FROM loadtest_seq
    WHERE n < member_count
    ORDER BY n;

    INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,
                                       created_at, updated_at)
    SELECT id, 1970 + FLOOR(RAND() * 31), IF(RAND() < 0.5, 'FEMALE', 'MALE'),
           SHA2(CONCAT('loadtest-', id), 256), 'FAKE', now_utc, now_utc, now_utc
    FROM member
    WHERE role = 'USER';

    SELECT MIN(id) INTO reporter FROM member WHERE role = 'USER';

    -- 장소별 난수를 먼저 임시 테이블에 고정한다. 조인 조건 안에서 RAND()를 부르면 비교할 때마다 값이 바뀌기 때문이다.
    DROP TEMPORARY TABLE IF EXISTS loadtest_stage;
    CREATE TEMPORARY TABLE loadtest_stage (
        n INT NOT NULL PRIMARY KEY,
        r_region DOUBLE NOT NULL,
        u1 DOUBLE NOT NULL,
        u2 DOUBLE NOT NULL,
        r_type DOUBLE NOT NULL,
        r_status DOUBLE NOT NULL,
        r_warning DOUBLE NOT NULL
    );

    WHILE inserted < spot_count DO
        SET chunk_rows = LEAST(chunk_size, spot_count - inserted);
        TRUNCATE TABLE loadtest_stage;
        -- 박스-뮬러 변환에 넣는 u1은 0이면 로그가 정의되지 않으므로 (0, 1] 범위로 만든다.
        INSERT INTO loadtest_stage (n, r_region, u1, u2, r_type, r_status, r_warning)
        SELECT inserted + n, RAND() * 100, 1 - RAND(), RAND(), RAND(), RAND(), RAND()
        FROM loadtest_seq
        WHERE n < chunk_rows;

        SELECT COALESCE(MAX(id), 0) INTO before_id FROM spot;

        INSERT INTO spot (type, name, location, address, weather_nx, weather_ny, park_warning,
                          status, created_at, updated_at)
        SELECT p.type,
               CONCAT('검증 ', CASE p.type WHEN 'BAKJI' THEN '박지' WHEN 'CAMPSITE' THEN '야영장' ELSE '휴양림' END,
                      ' ', LPAD(p.n + 1, 7, '0')),
               ST_SRID(POINT(p.lng, p.lat), 4326),
               NULL,
               -- 북위 38도·동경 126도가 격자 (43, 136)이고, 격자 한 칸이 5km라 경도 1도는 약 17.6칸, 위도 1도는 약 22.2칸이다.
               LEAST(149, GREATEST(1, ROUND(43 + (p.lng - 126) * 17.6))),
               LEAST(253, GREATEST(1, ROUND(136 + (p.lat - 38) * 22.2))),
               p.type = 'BAKJI' AND p.r_warning < 0.10,
               CASE
                   WHEN p.type <> 'BAKJI' THEN 'ACTIVE'
                   WHEN p.r_status < 0.0111 THEN 'PENDING_REVIEW'
                   WHEN p.r_status < 0.0333 THEN 'HIDDEN'
                   ELSE 'ACTIVE'
               END,
               now_utc, now_utc
        FROM (
            SELECT s.n, s.r_warning, s.r_status,
                   CASE WHEN s.r_type < 0.90 THEN 'BAKJI' WHEN s.r_type < 0.98 THEN 'CAMPSITE' ELSE 'FOREST' END AS type,
                   LEAST(38.6, GREATEST(33.0, g.lat + g.sigma * SQRT(-2 * LN(s.u1)) * COS(2 * PI() * s.u2))) AS lat,
                   LEAST(131.9, GREATEST(124.6, g.lng + g.sigma * SQRT(-2 * LN(s.u1)) * SIN(2 * PI() * s.u2))) AS lng
            FROM loadtest_stage s
            JOIN loadtest_region g ON s.r_region >= g.cum_lo AND s.r_region < g.cum_hi
        ) p
        ORDER BY p.n;

        INSERT INTO public_spot_detail (spot_id, source, external_id, synced_at, operating_status, created_at, updated_at)
        SELECT id, IF(type = 'CAMPSITE', 'GOCAMPING', 'FOREST'), CONCAT('LT-', id), now_utc,
               IF(RAND() < 0.05, 'TEMPORARILY_CLOSED', 'OPERATING'), now_utc, now_utc
        FROM spot
        WHERE id > before_id AND type IN ('CAMPSITE', 'FOREST');

        INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)
        SELECT id, reporter, RAND() < 0.5, RAND() < 0.5, now_utc, now_utc
        FROM spot
        WHERE id > before_id AND type = 'BAKJI';

        SET inserted = inserted + chunk_rows;
        SELECT inserted AS spots_inserted;
    END WHILE;

    DROP TEMPORARY TABLE loadtest_stage;
    DROP TEMPORARY TABLE loadtest_region;
    DROP TEMPORARY TABLE loadtest_seq;
END //

DELIMITER ;

CALL loadtest_seed(@spot_count, @member_count);
DROP PROCEDURE loadtest_seed;

ANALYZE TABLE spot, public_spot_detail, bakji_detail, member, identity_verification;
