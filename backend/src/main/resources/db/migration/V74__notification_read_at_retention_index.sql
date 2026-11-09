-- 읽은 지 90일 지난 알림을 매일 지운다. 정리 작업은 read_at 구간에서 메일을 기다리지 않는 알림 id를 읽은 시각 순으로 고른다.
-- (member_id, read_at) 인덱스는 member_id가 앞이라 이 조회에 쓸 수 없다. email_status까지 넣으면 행을 다시 읽지 않고 인덱스만으로 고른다.
CREATE INDEX idx_notification_read_at_email_status ON notification (read_at, email_status);
