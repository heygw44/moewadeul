-- 공개 추억 기록의 신고와 관리자 숨김을 위해 기록에 상태를 더하고 신고 테이블을 만든다.
-- status는 ACTIVE(정상), PENDING_REVIEW(검토 전 신고가 쌓여 검토 대기), HIDDEN(관리자가 숨김)이고 앱이 값을 검사한다.
-- 작성자가 기록을 지우면 행을 삭제하므로 삭제 상태는 두지 않는다. 지금 있는 기록은 모두 ACTIVE로 시작한다.
-- 상태는 공개 범위(visibility)와 따로 바뀐다. 공개 피드와 회원 추억지도는 둘 다 맞는 기록만 읽는다.
ALTER TABLE memory ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- 회원이 공개 추억 기록에 남기는 신고. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 한 회원은 같은 기록을 한 번만 신고할 수 있고, 이 제약은 유니크 키 (memory_id, reporter_id)가 맡는다.
-- reviewed_at이 NULL이면 관리자가 아직 검토하지 않은 신고다. 관리자가 기록을 복구하면서 검토 전 신고에 이 시각을 채운다.
-- 검토 전 신고의 수는 memory_id로 센다. 유니크 키의 앞부분이 memory_id라서 별도 인덱스는 두지 않는다.
-- 기록은 작성자가 지우거나 탈퇴할 때 행째 사라지므로 memory_id FK는 ON DELETE CASCADE로 두어 신고도 함께 지운다.
-- 회원은 행을 지우지 않고 익명화하므로 reporter_id FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE memory_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    memory_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(20) NOT NULL,
    content VARCHAR(1000) NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_memory_report_memory_reporter UNIQUE (memory_id, reporter_id),
    CONSTRAINT fk_memory_report_memory FOREIGN KEY (memory_id) REFERENCES memory (id) ON DELETE CASCADE,
    CONSTRAINT fk_memory_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
