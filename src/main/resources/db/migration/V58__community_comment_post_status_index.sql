-- 글 목록은 글마다 ACTIVE 댓글 수를 센다. post_id 인덱스에는 status가 없어서 댓글마다 행을 다시 읽었다(로컬 부하 DB 첫 쪽에서 요청마다 2.5만 행).
-- (post_id, status)이면 댓글 수를 인덱스만 읽고 센다.
-- 이 인덱스가 생기면 MySQL이 FK용으로 저절로 만든 fk_community_comment_post를 지우므로, 댓글 목록(post_id, parent_id IS NULL, id 순서)이 기본 키를 훑게 된다.
-- (post_id, parent_id)는 뒤에 id가 붙어서 댓글 목록을 정렬 없이 읽는다.
CREATE INDEX idx_community_comment_post_status ON community_comment (post_id, status);
CREATE INDEX idx_community_comment_post_parent ON community_comment (post_id, parent_id);
