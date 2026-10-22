-- 목록 화면이 원본 사진(최대 5MB)을 그대로 내려받으면 느린 회선에서 피드 한 페이지를 여는 데 몇 분이 걸린다.
-- 그래서 브라우저가 긴 변 480px 이하의 작은 JPEG 썸네일을 만들어 원본 옆에 객체 하나를 더 올리고,
-- 서버는 사진 행마다 그 썸네일의 객체 키와 업로드 URL 서명에 넣은 크기를 저장한다.
-- 키는 원본과 같은 UUID에 _thumb.jpg를 붙여 서버가 만든다. 원본처럼 같은 경로를 두 행이 가리키지 못하게 유니크로 둔다.
-- 이 변경 전에 올린 사진과 썸네일을 만들지 못한 브라우저가 올린 사진은 썸네일이 없다.
-- 그래서 두 컬럼은 NULL을 허용하고, 목록 조회는 썸네일이 없으면 원본 키를 쓴다.
-- 키만 있고 크기가 없거나 그 반대인 행은 업로드 확인을 할 수 없으므로, 두 컬럼이 함께 NULL이거나 함께 값이 있게 CHECK로 묶는다.
ALTER TABLE memory_image
    ADD COLUMN thumbnail_object_key VARCHAR(255) NULL AFTER size_bytes,
    ADD COLUMN thumbnail_size_bytes INT NULL AFTER thumbnail_object_key,
    ADD CONSTRAINT uk_memory_image_thumbnail_object_key UNIQUE (thumbnail_object_key),
    ADD CONSTRAINT ck_memory_image_thumbnail CHECK (
        (thumbnail_object_key IS NULL AND thumbnail_size_bytes IS NULL)
        OR (thumbnail_object_key IS NOT NULL AND thumbnail_size_bytes IS NOT NULL)
    );

ALTER TABLE community_image
    ADD COLUMN thumbnail_object_key VARCHAR(255) NULL AFTER size_bytes,
    ADD COLUMN thumbnail_size_bytes INT NULL AFTER thumbnail_object_key,
    ADD CONSTRAINT uk_community_image_thumbnail_object_key UNIQUE (thumbnail_object_key),
    ADD CONSTRAINT ck_community_image_thumbnail CHECK (
        (thumbnail_object_key IS NULL AND thumbnail_size_bytes IS NULL)
        OR (thumbnail_object_key IS NOT NULL AND thumbnail_size_bytes IS NOT NULL)
    );
