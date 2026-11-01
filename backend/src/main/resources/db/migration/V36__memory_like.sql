-- 회원이 공개 추억 기록에 누르는 좋아요. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 한 회원은 한 기록에 좋아요를 한 번만 누를 수 있고, 이 제약은 기본 키 (memory_id, member_id)가 맡는다.
-- 좋아요를 취소하면 행을 지운다.
-- 좋아요 수는 저장하지 않고 memory_id로 센다. 기본 키의 앞부분이 memory_id라서 별도 인덱스는 두지 않는다.
-- member_id에는 FK 인덱스가 생겨서 회원별로도 찾을 수 있다.
-- 기록은 작성자가 지우거나 탈퇴할 때 행째 사라지므로 memory_id FK는 ON DELETE CASCADE로 두어 좋아요도 함께 지운다.
-- 회원은 행을 지우지 않고 익명화하므로 member_id FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE memory_like (
    memory_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (memory_id, member_id),
    CONSTRAINT fk_memory_like_memory FOREIGN KEY (memory_id) REFERENCES memory (id) ON DELETE CASCADE,
    CONSTRAINT fk_memory_like_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
