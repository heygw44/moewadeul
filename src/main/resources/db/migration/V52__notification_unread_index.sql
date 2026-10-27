-- 화면은 경로가 바뀔 때마다, 탭이 다시 보일 때마다 회원의 안 읽은 알림 수를 센다.
-- (member_id, id) 인덱스만 있으면 이 조회가 회원의 알림을 모두 찾은 뒤 행마다 read_at을 확인해서,
-- 읽은 알림이 쌓일수록 느려진다. read_at까지 인덱스에 넣으면 안 읽은 알림만 인덱스에서 세고 행을 다시 읽지 않는다.
-- (member_id, id)는 알림 목록(최신순)이 쓰므로 그대로 둔다.
CREATE INDEX idx_notification_member_read ON notification (member_id, read_at);
