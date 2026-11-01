-- 장소 후기에 붙이는 이미지. 커뮤니티 글 이미지(community_image)와 같은 구조이고, 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 서버는 업로드 URL을 발급할 때 review_id가 NULL인 행을 먼저 만든다. 후기에 붙이면 review_id와 display_order를 채우고,
-- 후기를 고치면서 뺀 이미지는 두 컬럼을 다시 NULL로 되돌린다.
-- 후기는 작성자가 지우면 행이 삭제되므로, FK가 붙은 이미지의 review_id를 NULL로 바꾸게(ON DELETE SET NULL) 둔다.
-- 그러면 그 이미지도 붙지 않은 업로드가 되고, 정리 작업이 만든 지 24시간이 지난 행을 저장소 객체와 함께 지운다.
-- 정리 작업용 인덱스는 아직 두지 않는다. 인덱스는 실행 계획을 확인한 뒤에 필요한 것만 추가한다.
-- review_id의 FK가 만드는 인덱스를 후기별 이미지 조회에 쓴다.
-- object_key는 저장소 안의 객체 경로이고 서버가 만든다. 같은 경로를 두 행이 가리키지 못하게 유니크로 둔다.
-- size_bytes는 업로드 URL 서명에 넣은 크기이고, display_order는 후기 안 순서(0~2)다.
CREATE TABLE review_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    review_id BIGINT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    size_bytes INT NOT NULL,
    display_order TINYINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_image_object_key UNIQUE (object_key),
    CONSTRAINT fk_review_image_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_review_image_review FOREIGN KEY (review_id) REFERENCES spot_review (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
