-- 박지 재판정과 고캠핑 동기화가 spot_cluster_total 행에서, 동시에 들어온 박지 제보 둘이 bakji_detail 끝 틈에서 교착했다(#838).
-- 두 장소 트리거가 결과에 쓰지 않는 잠금을 잡지 않게 다시 만든다. 집계 결과는 V33과 같다.

DROP TRIGGER trg_spot_cluster_after_insert;
DROP TRIGGER trg_spot_cluster_after_update;

DELIMITER //
-- fk_bakji_detail_spot 때문에 장소 행보다 먼저 상세 행이 있을 수 없어서 물·화장실은 항상 FALSE다.
-- 서브쿼리로 읽으면 bakji_detail 맨 끝 틈에 잠금이 걸리므로 읽지 않는다. 상세 행은 bakji_detail 트리거가 옮긴다.
CREATE TRIGGER trg_spot_cluster_after_insert AFTER INSERT ON spot
FOR EACH ROW
BEGIN
    IF NEW.status = 'ACTIVE' THEN
        CALL spot_cluster_total_apply(ST_Latitude(NEW.location), ST_Longitude(NEW.location), 1);
        CALL spot_cluster_cell_apply(
            ST_Latitude(NEW.location), ST_Longitude(NEW.location), NEW.type, FALSE, FALSE, NEW.park_warning, 1);
    END IF;
END //

-- 상태, 좌표, 유형, 경고가 하나도 바뀌지 않은 UPDATE(이름·설명 수정, 판정 시각 갱신 등)는 집계를 건드리지 않는다.
-- spot_cluster_total의 키에는 유형과 경고가 없다. 둘만 바뀌면 -1, +1이 상쇄되므로 행을 잠그지 않도록 건너뛴다.
CREATE TRIGGER trg_spot_cluster_after_update AFTER UPDATE ON spot
FOR EACH ROW
BEGIN
    DECLARE v_has_water BOOLEAN;
    DECLARE v_has_toilet BOOLEAN;
    DECLARE v_total_changed BOOLEAN;
    IF NOT (OLD.status <=> NEW.status AND ST_Equals(OLD.location, NEW.location)
            AND OLD.type <=> NEW.type AND OLD.park_warning <=> NEW.park_warning) THEN
        SET v_has_water = COALESCE((SELECT b.has_water FROM bakji_detail b WHERE b.spot_id = NEW.id), FALSE);
        SET v_has_toilet = COALESCE((SELECT b.has_toilet FROM bakji_detail b WHERE b.spot_id = NEW.id), FALSE);
        SET v_total_changed = NOT (OLD.status <=> NEW.status AND ST_Equals(OLD.location, NEW.location));
        IF OLD.status = 'ACTIVE' THEN
            IF v_total_changed THEN
                CALL spot_cluster_total_apply(ST_Latitude(OLD.location), ST_Longitude(OLD.location), -1);
            END IF;
            CALL spot_cluster_cell_apply(
                ST_Latitude(OLD.location), ST_Longitude(OLD.location), OLD.type, v_has_water, v_has_toilet,
                OLD.park_warning, -1);
        END IF;
        IF NEW.status = 'ACTIVE' THEN
            IF v_total_changed THEN
                CALL spot_cluster_total_apply(ST_Latitude(NEW.location), ST_Longitude(NEW.location), 1);
            END IF;
            CALL spot_cluster_cell_apply(
                ST_Latitude(NEW.location), ST_Longitude(NEW.location), NEW.type, v_has_water, v_has_toilet,
                NEW.park_warning, 1);
        END IF;
    END IF;
END //
DELIMITER ;
