-- 지도 묶음을 미리 세어 두는 격자 표와, 장소가 바뀔 때 그 표를 맞추는 트리거.
--
-- 지도 영역 조회는 화면 안 장소가 500개를 넘으면 화면을 20×20칸으로 나눠 칸마다 장소 수와 평균 좌표를 준다.
-- 서버가 이 값을 요청마다 영역 안 장소를 모두 읽어 세면, 넓은 영역에서는 요청 하나가 장소 수십만 건을 읽는다.
-- 장소 100만 건, 동시 300명으로 측정해 보니 MySQL CPU가 한도에 닿아 응답이 수십 초씩 밀렸다.
-- 그래서 고정 격자 칸마다 ACTIVE 장소 수와 위도·경도 합을 미리 세어 두고, 서버는 화면에 든 격자 칸만 합친다.
--
-- 격자는 0.0025도 × 2^단계(단계 0~7, 0.0025도~0.32도)다. 칸 번호는 FLOOR(위도 / 칸 크기), FLOOR(경도 / 칸 크기)다.
-- 서버의 조회 SQL도 같은 식으로 칸 크기와 칸 중심을 계산한다. 그래서 개발자가 단계 수나 칸 크기를 바꾸면 조회 SQL도 함께 고쳐야 한다.
--
-- spot_cluster_total은 필터 없는 요청에 쓰고, spot_cluster_cell은 유형·물·화장실·경고 필터를 켠 요청에 쓴다.
-- 필터 없는 요청이 spot_cluster_cell을 쓰면 칸마다 조합 행을 모두 더해야 해서 몇 배 느렸다.
-- 박지가 아닌 장소와 상세 행이 아직 없는 박지는 물·화장실을 FALSE로 센다. 지도 필터는 박지에만 시설 조건을 걸기 때문이다.
-- 평균 좌표는 위도·경도 합을 장소 수로 나눠 구한다.
--
-- 휴장 제외는 오늘 날짜에 따라 결과가 바뀌어서 트리거로 셀 수 없다. 그래서 서버가 요청 때 휴장 후보만 읽어 빼 준다.
-- closure_candidate는 휴장 기간이 하나라도 있거나 운영 상태가 임시 휴장·폐업인 공공 장소다. 날짜와 상관없이 휴장일 수 있는 장소만 고른다.

CREATE TABLE spot_cluster_total (
    grid_level TINYINT NOT NULL,
    cell_row INT NOT NULL,
    cell_col INT NOT NULL,
    spot_count INT NOT NULL,
    lat_sum DOUBLE NOT NULL,
    lng_sum DOUBLE NOT NULL,
    PRIMARY KEY (grid_level, cell_row, cell_col)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE spot_cluster_cell (
    grid_level TINYINT NOT NULL,
    cell_row INT NOT NULL,
    cell_col INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    has_water BOOLEAN NOT NULL,
    has_toilet BOOLEAN NOT NULL,
    park_warning BOOLEAN NOT NULL,
    spot_count INT NOT NULL,
    lat_sum DOUBLE NOT NULL,
    lng_sum DOUBLE NOT NULL,
    PRIMARY KEY (grid_level, cell_row, cell_col, type, has_water, has_toilet, park_warning)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE public_spot_detail
    ADD COLUMN closure_candidate BOOLEAN AS (
        COALESCE(closed_from IS NOT NULL OR closed_until IS NOT NULL
                 OR operating_status IN ('TEMPORARILY_CLOSED', 'PERMANENTLY_CLOSED'), FALSE)) STORED NOT NULL,
    ADD INDEX idx_public_spot_detail_closure_candidate (closure_candidate);

-- 장소 한 곳을 8단계 격자 모두에 더하거나(p_delta = 1) 뺀다(p_delta = -1).
-- 빼는 쪽은 INSERT 없이 UPDATE만 하고, 0건이 된 행은 지운다. 그래서 집계 표가 먼저 비워진 뒤 장소를 지워도 음수 행이 생기지 않는다.
DELIMITER //
CREATE PROCEDURE spot_cluster_total_apply(IN p_lat DOUBLE, IN p_lng DOUBLE, IN p_delta INT)
BEGIN
    DECLARE v_level TINYINT DEFAULT 0;
    DECLARE v_size DOUBLE;
    DECLARE v_row INT;
    DECLARE v_col INT;
    WHILE v_level < 8 DO
        SET v_size = 0.0025 * POW(2, v_level);
        SET v_row = FLOOR(p_lat / v_size);
        SET v_col = FLOOR(p_lng / v_size);
        IF p_delta > 0 THEN
            INSERT INTO spot_cluster_total (grid_level, cell_row, cell_col, spot_count, lat_sum, lng_sum)
            VALUES (v_level, v_row, v_col, 1, p_lat, p_lng)
            ON DUPLICATE KEY UPDATE spot_count = spot_count + 1, lat_sum = lat_sum + p_lat, lng_sum = lng_sum + p_lng;
        ELSE
            UPDATE spot_cluster_total
            SET spot_count = spot_count - 1, lat_sum = lat_sum - p_lat, lng_sum = lng_sum - p_lng
            WHERE grid_level = v_level AND cell_row = v_row AND cell_col = v_col;
            DELETE FROM spot_cluster_total
            WHERE grid_level = v_level AND cell_row = v_row AND cell_col = v_col AND spot_count <= 0;
        END IF;
        SET v_level = v_level + 1;
    END WHILE;
END //

CREATE PROCEDURE spot_cluster_cell_apply(
    IN p_lat DOUBLE, IN p_lng DOUBLE, IN p_type VARCHAR(20), IN p_has_water BOOLEAN, IN p_has_toilet BOOLEAN,
    IN p_park_warning BOOLEAN, IN p_delta INT)
BEGIN
    DECLARE v_level TINYINT DEFAULT 0;
    DECLARE v_size DOUBLE;
    DECLARE v_row INT;
    DECLARE v_col INT;
    WHILE v_level < 8 DO
        SET v_size = 0.0025 * POW(2, v_level);
        SET v_row = FLOOR(p_lat / v_size);
        SET v_col = FLOOR(p_lng / v_size);
        IF p_delta > 0 THEN
            INSERT INTO spot_cluster_cell (grid_level, cell_row, cell_col, type, has_water, has_toilet, park_warning,
                                           spot_count, lat_sum, lng_sum)
            VALUES (v_level, v_row, v_col, p_type, p_has_water, p_has_toilet, p_park_warning, 1, p_lat, p_lng)
            ON DUPLICATE KEY UPDATE spot_count = spot_count + 1, lat_sum = lat_sum + p_lat, lng_sum = lng_sum + p_lng;
        ELSE
            UPDATE spot_cluster_cell
            SET spot_count = spot_count - 1, lat_sum = lat_sum - p_lat, lng_sum = lng_sum - p_lng
            WHERE grid_level = v_level AND cell_row = v_row AND cell_col = v_col AND type = p_type
              AND has_water = p_has_water AND has_toilet = p_has_toilet AND park_warning = p_park_warning;
            DELETE FROM spot_cluster_cell
            WHERE grid_level = v_level AND cell_row = v_row AND cell_col = v_col AND type = p_type
              AND has_water = p_has_water AND has_toilet = p_has_toilet AND park_warning = p_park_warning
              AND spot_count <= 0;
        END IF;
        SET v_level = v_level + 1;
    END WHILE;
END //

-- 트리거는 생성 컬럼(latitude, longitude) 대신 ST_Latitude, ST_Longitude로 좌표를 읽는다.
-- 트리거 안에서 생성 컬럼 값이 언제 채워지는지에 기대지 않으려는 것이다. 두 식은 같은 값을 낸다.
-- 박지의 물·화장실은 bakji_detail에서 읽는다. 상세 행이 없으면 서브쿼리가 NULL이라 FALSE로 센다.
CREATE TRIGGER trg_spot_cluster_after_insert AFTER INSERT ON spot
FOR EACH ROW
BEGIN
    IF NEW.status = 'ACTIVE' THEN
        CALL spot_cluster_total_apply(ST_Latitude(NEW.location), ST_Longitude(NEW.location), 1);
        CALL spot_cluster_cell_apply(
            ST_Latitude(NEW.location), ST_Longitude(NEW.location), NEW.type,
            COALESCE((SELECT b.has_water FROM bakji_detail b WHERE b.spot_id = NEW.id), FALSE),
            COALESCE((SELECT b.has_toilet FROM bakji_detail b WHERE b.spot_id = NEW.id), FALSE),
            NEW.park_warning, 1);
    END IF;
END //

-- 상태, 좌표, 유형, 경고가 하나도 바뀌지 않은 UPDATE(이름·설명 수정, 판정 시각 갱신 등)는 집계를 건드리지 않는다.
CREATE TRIGGER trg_spot_cluster_after_update AFTER UPDATE ON spot
FOR EACH ROW
BEGIN
    DECLARE v_has_water BOOLEAN;
    DECLARE v_has_toilet BOOLEAN;
    IF NOT (OLD.status <=> NEW.status AND ST_Equals(OLD.location, NEW.location)
            AND OLD.type <=> NEW.type AND OLD.park_warning <=> NEW.park_warning) THEN
        SET v_has_water = COALESCE((SELECT b.has_water FROM bakji_detail b WHERE b.spot_id = NEW.id), FALSE);
        SET v_has_toilet = COALESCE((SELECT b.has_toilet FROM bakji_detail b WHERE b.spot_id = NEW.id), FALSE);
        IF OLD.status = 'ACTIVE' THEN
            CALL spot_cluster_total_apply(ST_Latitude(OLD.location), ST_Longitude(OLD.location), -1);
            CALL spot_cluster_cell_apply(
                ST_Latitude(OLD.location), ST_Longitude(OLD.location), OLD.type, v_has_water, v_has_toilet,
                OLD.park_warning, -1);
        END IF;
        IF NEW.status = 'ACTIVE' THEN
            CALL spot_cluster_total_apply(ST_Latitude(NEW.location), ST_Longitude(NEW.location), 1);
            CALL spot_cluster_cell_apply(
                ST_Latitude(NEW.location), ST_Longitude(NEW.location), NEW.type, v_has_water, v_has_toilet,
                NEW.park_warning, 1);
        END IF;
    END IF;
END //

CREATE TRIGGER trg_spot_cluster_after_delete AFTER DELETE ON spot
FOR EACH ROW
BEGIN
    IF OLD.status = 'ACTIVE' THEN
        CALL spot_cluster_total_apply(ST_Latitude(OLD.location), ST_Longitude(OLD.location), -1);
        CALL spot_cluster_cell_apply(
            ST_Latitude(OLD.location), ST_Longitude(OLD.location), OLD.type,
            COALESCE((SELECT b.has_water FROM bakji_detail b WHERE b.spot_id = OLD.id), FALSE),
            COALESCE((SELECT b.has_toilet FROM bakji_detail b WHERE b.spot_id = OLD.id), FALSE),
            OLD.park_warning, -1);
    END IF;
END //

-- 박지를 제보하면 서버는 spot 행을 먼저 넣고 bakji_detail 행을 넣는다. spot 트리거는 그때 물·화장실을 FALSE로 셌으므로,
-- 상세 행이 생기거나 시설 값이 바뀌면 그 장소를 시설 조합 행 사이에서 옮긴다. 장소 수와 합계는 그대로라 spot_cluster_total은 건드리지 않는다.
CREATE TRIGGER trg_bakji_detail_cluster_after_insert AFTER INSERT ON bakji_detail
FOR EACH ROW
BEGIN
    DECLARE v_lat DOUBLE;
    DECLARE v_lng DOUBLE;
    DECLARE v_type VARCHAR(20);
    DECLARE v_park_warning BOOLEAN;
    DECLARE v_status VARCHAR(20);
    SELECT ST_Latitude(s.location), ST_Longitude(s.location), s.type, s.park_warning, s.status
    INTO v_lat, v_lng, v_type, v_park_warning, v_status
    FROM spot s WHERE s.id = NEW.spot_id;
    IF v_status = 'ACTIVE' AND (NEW.has_water OR NEW.has_toilet) THEN
        CALL spot_cluster_cell_apply(v_lat, v_lng, v_type, FALSE, FALSE, v_park_warning, -1);
        CALL spot_cluster_cell_apply(v_lat, v_lng, v_type, NEW.has_water, NEW.has_toilet, v_park_warning, 1);
    END IF;
END //

CREATE TRIGGER trg_bakji_detail_cluster_after_update AFTER UPDATE ON bakji_detail
FOR EACH ROW
BEGIN
    DECLARE v_lat DOUBLE;
    DECLARE v_lng DOUBLE;
    DECLARE v_type VARCHAR(20);
    DECLARE v_park_warning BOOLEAN;
    DECLARE v_status VARCHAR(20);
    IF NOT (OLD.has_water <=> NEW.has_water AND OLD.has_toilet <=> NEW.has_toilet) THEN
        SELECT ST_Latitude(s.location), ST_Longitude(s.location), s.type, s.park_warning, s.status
        INTO v_lat, v_lng, v_type, v_park_warning, v_status
        FROM spot s WHERE s.id = NEW.spot_id;
        IF v_status = 'ACTIVE' THEN
            CALL spot_cluster_cell_apply(v_lat, v_lng, v_type, OLD.has_water, OLD.has_toilet, v_park_warning, -1);
            CALL spot_cluster_cell_apply(v_lat, v_lng, v_type, NEW.has_water, NEW.has_toilet, v_park_warning, 1);
        END IF;
    END IF;
END //

CREATE TRIGGER trg_bakji_detail_cluster_after_delete AFTER DELETE ON bakji_detail
FOR EACH ROW
BEGIN
    DECLARE v_lat DOUBLE;
    DECLARE v_lng DOUBLE;
    DECLARE v_type VARCHAR(20);
    DECLARE v_park_warning BOOLEAN;
    DECLARE v_status VARCHAR(20);
    SELECT ST_Latitude(s.location), ST_Longitude(s.location), s.type, s.park_warning, s.status
    INTO v_lat, v_lng, v_type, v_park_warning, v_status
    FROM spot s WHERE s.id = OLD.spot_id;
    IF v_status = 'ACTIVE' AND (OLD.has_water OR OLD.has_toilet) THEN
        CALL spot_cluster_cell_apply(v_lat, v_lng, v_type, OLD.has_water, OLD.has_toilet, v_park_warning, -1);
        CALL spot_cluster_cell_apply(v_lat, v_lng, v_type, FALSE, FALSE, v_park_warning, 1);
    END IF;
END //
DELIMITER ;

-- 이미 있는 장소로 처음 한 번 채운다. 칸 번호 식은 위 프로시저와 같다.
INSERT INTO spot_cluster_total (grid_level, cell_row, cell_col, spot_count, lat_sum, lng_sum)
SELECT g.grid_level,
       FLOOR(s.latitude / (0.0025 * POW(2, g.grid_level))),
       FLOOR(s.longitude / (0.0025 * POW(2, g.grid_level))),
       COUNT(*), SUM(s.latitude), SUM(s.longitude)
FROM spot s
CROSS JOIN (SELECT 0 AS grid_level UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
            UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) g
WHERE s.status = 'ACTIVE'
GROUP BY 1, 2, 3;

INSERT INTO spot_cluster_cell (grid_level, cell_row, cell_col, type, has_water, has_toilet, park_warning,
                               spot_count, lat_sum, lng_sum)
SELECT g.grid_level,
       FLOOR(s.latitude / (0.0025 * POW(2, g.grid_level))),
       FLOOR(s.longitude / (0.0025 * POW(2, g.grid_level))),
       s.type, COALESCE(b.has_water, FALSE), COALESCE(b.has_toilet, FALSE), s.park_warning,
       COUNT(*), SUM(s.latitude), SUM(s.longitude)
FROM spot s
LEFT JOIN bakji_detail b ON b.spot_id = s.id
CROSS JOIN (SELECT 0 AS grid_level UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
            UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) g
WHERE s.status = 'ACTIVE'
GROUP BY 1, 2, 3, 4, 5, 6, 7;
