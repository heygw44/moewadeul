-- 회원이 베이스캠프 없이 혼자 가는 야영 일정. 날짜는 한국 날짜이고, 종료일은 집으로 돌아가는 날이라 그 밤은 세지 않는다.
-- 수정 없이 등록과 삭제만 하므로 updated_at을 두지 않는다. 회원이 지우면 행을 삭제한다.
-- 출발일 범위, 박 수, 회원별 개수와 밤 겹침은 앱이 저장 전에 검사한다. 밤 겹침은 행 사이의 조건이라 DB 제약으로 막지 않는다.
-- (member_id, end_date) 인덱스는 회원의 진행 예정 일정(종료일이 오늘보다 뒤)을 세고 겹침을 확인할 때 쓰고,
-- (spot_id, end_date) 인덱스는 장소 상세가 그 장소의 남은 일정을 읽어 예상 인원에 더할 때 쓴다.
-- 장소와 회원은 지우지 않고 숨기거나 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE solo_trip (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    spot_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_solo_trip_member_end_date (member_id, end_date),
    INDEX idx_solo_trip_spot_end_date (spot_id, end_date),
    CONSTRAINT ck_solo_trip_dates CHECK (end_date > start_date),
    CONSTRAINT fk_solo_trip_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_solo_trip_spot FOREIGN KEY (spot_id) REFERENCES spot (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
