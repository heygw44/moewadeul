-- 관리자 댓글·기록 검토 목록은 status로 거른다. 두 표에 status 인덱스가 없어 요청마다 표 전체를 읽었다(부하 시험 데이터에서 댓글 54.8만 행, 기록 10만 행).
-- updated_at은 넣지 않는다. 글을 고칠 때마다 updated_at이 바뀌어 인덱스도 함께 고쳐야 하는데, 검토 대기 행은 수천 건이라 정렬은 인덱스 없이도 싸다.
-- 글(community_post)과 장소(spot)에는 status로 시작하는 인덱스가 이미 있다.
CREATE INDEX idx_community_comment_status ON community_comment (status);
CREATE INDEX idx_memory_status ON memory (status);
