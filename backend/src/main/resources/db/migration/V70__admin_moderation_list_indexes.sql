-- 관리자 댓글·기억 목록은 status로 거르고 updated_at 순서로 정렬한다. 두 테이블에 status 인덱스가 없어 요청마다 전체를 훑었다(로컬 부하 DB 댓글 54.8만 행, 기억 10만 행).
-- (status, updated_at)이면 거르기와 정렬을 인덱스로 끝낸다. 글(community_post)과 장소(spot)에는 status로 시작하는 인덱스가 이미 있다.
CREATE INDEX idx_community_comment_status_updated_at ON community_comment (status, updated_at);
CREATE INDEX idx_memory_status_updated_at ON memory (status, updated_at);
