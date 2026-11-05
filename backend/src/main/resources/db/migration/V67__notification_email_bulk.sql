-- 한 이벤트로 수백 통이 생기는 메일이 밀려도 개별 메일이 그 뒤에 서지 않게 발송 순서를 나눈다.
-- 발송 작업은 FOR UPDATE SKIP LOCKED로 읽으므로, 파일 정렬이면 대기 행 전부를 잠근다. 그래서 정렬까지 인덱스로 끝낸다.
ALTER TABLE notification ADD COLUMN email_bulk BOOLEAN NOT NULL DEFAULT FALSE AFTER email_status;

CREATE INDEX idx_notification_email_status_bulk_id ON notification (email_status, email_bulk, id);

DROP INDEX idx_notification_email_status_id ON notification;
