-- 후기 목록과 장소 상세가 원본 사진(최대 5MB)을 그대로 내려받으면 느린 회선에서 후기 한 페이지를 여는 데 오래 걸린다.
-- 그래서 추억 기록·커뮤니티 사진(V47)처럼 브라우저가 긴 변 480px 이하의 작은 JPEG 썸네일을 만들어 원본 옆에 객체 하나를 더 올리고,
-- 서버는 사진 행마다 그 썸네일의 객체 키와 업로드 URL 서명에 넣은 크기를 저장한다.
-- 이 변경 전에 올린 사진과 썸네일을 만들지 못한 브라우저가 올린 사진은 썸네일이 없다.
-- 그래서 두 컬럼은 NULL을 허용하고, 목록 조회는 썸네일이 없으면 원본 키를 쓴다.
-- 키만 있고 크기가 없거나 그 반대인 행은 업로드 확인을 할 수 없으므로, 두 컬럼이 함께 NULL이거나 함께 값이 있게 CHECK로 묶는다.
ALTER TABLE review_image
    ADD COLUMN thumbnail_object_key VARCHAR(255) NULL AFTER size_bytes,
    ADD COLUMN thumbnail_size_bytes INT NULL AFTER thumbnail_object_key,
    ADD CONSTRAINT uk_review_image_thumbnail_object_key UNIQUE (thumbnail_object_key),
    ADD CONSTRAINT ck_review_image_thumbnail CHECK (
        (thumbnail_object_key IS NULL AND thumbnail_size_bytes IS NULL)
        OR (thumbnail_object_key IS NOT NULL AND thumbnail_size_bytes IS NOT NULL)
    );
