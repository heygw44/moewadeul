-- 회원은 답글에도 답할 수 있다. 하지만 답글은 한 단계만 두므로, 앱은 새 답글의 parent_id를 답한 답글의 최상위 댓글로 정한다.
-- 그러면 parent_id만으로는 누구에게 답했는지 알 수 없다. 그래서 답한 답글을 reply_to_comment_id에 따로 둔다.
-- 최상위 댓글과 최상위 댓글에 바로 단 답글은 NULL이다. 목록은 이 댓글 작성자의 현재 닉네임을 읽어서, 닉네임을 바꾸거나 탈퇴해 익명화돼도 그대로 따라간다.
-- 댓글은 지워도 행을 남기므로 FK는 기본 동작(RESTRICT)으로 둔다. FK 인덱스는 MySQL이 만든다.
ALTER TABLE community_comment
    ADD COLUMN reply_to_comment_id BIGINT NULL AFTER parent_id,
    ADD CONSTRAINT fk_community_comment_reply_to FOREIGN KEY (reply_to_comment_id) REFERENCES community_comment (id);
