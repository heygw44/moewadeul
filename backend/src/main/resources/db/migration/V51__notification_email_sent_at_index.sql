-- 알림 메일 발송 작업이 메일을 집기 전에 최근 24시간에 보낸 알림 메일 수를 센다.
-- 운영 메일 계정의 하루 발송 한도를 알림 메일이 다 쓰지 않게 하려는 상한 계산이다.
-- 인덱스가 없으면 이 조회가 알림 표 전체를 읽는다. 발송 작업은 이 조회를 1초마다 하므로,
-- 보낸 시각 구간만 읽도록 email_sent_at에 인덱스를 둔다. 보내지 않은 알림은 값이 NULL이라 인덱스를 거의 늘리지 않는다.
CREATE INDEX idx_notification_email_sent_at ON notification (email_sent_at);
