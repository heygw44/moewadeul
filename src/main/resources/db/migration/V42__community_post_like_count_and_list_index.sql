-- 커뮤니티 글의 좋아요 수. 좋아요·취소 트랜잭션이 글 행을 잠근 뒤 community_post_like 행 수를 다시 세어 쓴다.
-- 처음에는 조회할 때마다 셌다. 그런데 글 20만 건·좋아요 121만 건에서 인기글 개수 세기가 활성 글마다 좋아요를 세느라 좋아요 표 전체를 읽었다.
-- 이미 있는 글은 지금 좋아요 행 수로 채운다.
ALTER TABLE community_post ADD COLUMN like_count INT NOT NULL DEFAULT 0;

UPDATE community_post p
JOIN (SELECT post_id, COUNT(*) AS cnt FROM community_post_like GROUP BY post_id) l ON l.post_id = p.id
SET p.like_count = l.cnt;

-- 글 목록과 인기글 목록이 상태 조건, 최신순(id 내림차순), 좋아요 수 조건을 이 인덱스만 읽고 끝내게 한다.
-- 글 행에는 본문이 들어 있어서, 개수를 세거나 OFFSET만큼 건너뛰려고 글 행을 읽으면 테이블이 버퍼 풀보다 클 때 요청마다 디스크를 읽는다.
CREATE INDEX idx_community_post_status_id_like_count ON community_post (status, id, like_count);
