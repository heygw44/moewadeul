-- 업로드 한도(#711)는 올리고 아직 붙이지 않은 이미지만 센다. 글·후기·기록을 지우거나 고쳐서 떨어진 이미지는
-- 붙이지 않은 이미지와 행 모양이 같아서, 한 번이라도 붙었는지를 따로 남긴다. 정리 작업은 이 값을 보지 않는다.
ALTER TABLE community_image ADD COLUMN ever_attached BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE review_image ADD COLUMN ever_attached BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE memory_image ADD COLUMN ever_attached BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE community_image SET ever_attached = TRUE WHERE post_id IS NOT NULL;
UPDATE review_image SET ever_attached = TRUE WHERE review_id IS NOT NULL;
UPDATE memory_image SET ever_attached = TRUE WHERE memory_id IS NOT NULL;
