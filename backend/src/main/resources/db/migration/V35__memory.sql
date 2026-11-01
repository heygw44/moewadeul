-- 회원이 다녀온 곳을 사진과 글로 남기는 추억 기록과 그 사진. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 좌표는 공간 컬럼이 아니라 위도·경도 두 컬럼으로 둔다. 지금은 영역이나 반경으로 찾는 조회가 없고,
-- 회원 한 명의 기록을 모두 읽거나 기록 한 건을 읽기 때문이다. 내 기록 목록은 member_id의 FK가 만드는 인덱스로 고른다.
-- 장소 연결은 선택이다. 장소를 연결해도 좌표는 회원이 보낸 값을 그대로 저장한다.
-- 박 수(0~30)와 좌표 범위는 행 하나로 판단할 수 있어서 CHECK로 둔다. 방문일이 오늘 이전인지는 시각이 필요해서 앱이 검사한다.
-- 글은 쓰지 않아도 되므로 NULL을 허용한다. 공개 범위는 PRIVATE(작성자만), PUBLIC(누구나)이고 기본은 PRIVATE다.
-- 작성자가 지우면 행을 삭제한다. 그래서 상태 컬럼이 없다.
CREATE TABLE memory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    spot_id BIGINT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    visited_date DATE NOT NULL,
    nights TINYINT NOT NULL,
    content VARCHAR(2000) NULL,
    visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_memory_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_memory_spot FOREIGN KEY (spot_id) REFERENCES spot (id),
    CONSTRAINT ck_memory_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_memory_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_memory_nights CHECK (nights BETWEEN 0 AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 기록에 붙이는 사진. 장소 후기 이미지(review_image)와 같은 구조다.
-- 서버는 업로드 URL을 발급할 때 memory_id가 NULL인 행을 먼저 만든다. 기록에 붙이면 memory_id와 display_order를 채우고,
-- 기록을 고치면서 뺀 사진은 두 컬럼을 다시 NULL로 되돌린다.
-- 기록을 지우면 FK가 memory_id를 NULL로 바꾼다(ON DELETE SET NULL). 그러면 그 사진도 붙지 않은 업로드가 되고,
-- 정리 작업이 만든 지 24시간이 지난 행을 저장소 객체와 함께 지운다.
-- 정리 작업용 인덱스는 아직 두지 않는다. 기록별 사진 조회는 memory_id의 FK가 만드는 인덱스를,
-- 탈퇴한 회원의 사진 조회는 member_id의 FK가 만드는 인덱스를 쓴다.
-- object_key는 저장소 안의 객체 경로이고 서버가 만든다. 같은 경로를 두 행이 가리키지 못하게 유니크로 둔다.
-- size_bytes는 업로드 URL 서명에 넣은 크기이고, display_order는 기록 안 순서(0~9)다.
CREATE TABLE memory_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    memory_id BIGINT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    size_bytes INT NOT NULL,
    display_order TINYINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_memory_image_object_key UNIQUE (object_key),
    CONSTRAINT fk_memory_image_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_memory_image_memory FOREIGN KEY (memory_id) REFERENCES memory (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
