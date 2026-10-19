-- 알림 메일을 이벤트 처리와 따로 보내려고 알림마다 메일 상태와 재시도 정보를 둔다.
-- 예전에는 알림 처리기가 메일을 한 통씩 보내서, 받는 회원이 수천 명인 이벤트 하나가 아웃박스 발행기를 수 분 붙잡았다.
-- 그동안 인증 코드 메일과 다른 알림 이벤트, 같은 스케줄러 스레드를 쓰는 결제 만료 작업까지 기다렸다.
-- 이제 처리기는 알림만 저장하고, 메일 발송 작업이 PENDING 알림을 집어 보낸다. 실패하면 그 알림만 다시 시도한다.
ALTER TABLE notification
    ADD COLUMN email_status VARCHAR(20) NOT NULL DEFAULT 'SKIPPED' AFTER read_at,
    ADD COLUMN email_attempt_count INT NOT NULL DEFAULT 0 AFTER email_status,
    ADD COLUMN email_next_attempt_at DATETIME(6) NULL AFTER email_attempt_count;

-- 이미 보낸 알림은 SENT다.
UPDATE notification SET email_status = 'SENT' WHERE email_sent_at IS NOT NULL;

-- 아직 발행 중인 이벤트(메일 실패로 재시도를 기다리는 이벤트 포함)의 알림은 새 발송 작업이 이어서 보내도록 PENDING으로 둔다.
-- 새 코드의 처리기는 이미 저장된 알림을 건너뛰므로, 여기서 넘기지 않으면 이 알림들의 메일은 나가지 않는다.
-- 받을 수 없는 회원이나 이메일을 끈 회원이면 발송 작업이 집을 때 SKIPPED로 바꾼다.
-- 나머지(이메일을 끈 회원, 메일 재시도 한도를 넘긴 이벤트 등)는 예전 방식으로 이미 끝난 알림이라 SKIPPED로 둔다.
UPDATE notification n
    JOIN outbox_event o ON o.id = CAST(SUBSTRING_INDEX(n.dedup_key, ':', 1) AS UNSIGNED)
SET n.email_status = 'PENDING'
WHERE n.email_sent_at IS NULL
  AND o.status = 'PENDING';

-- 기본값은 기존 행을 채우는 데만 썼다. 새 행은 앱이 상태를 정해서 넣는다.
ALTER TABLE notification ALTER COLUMN email_status DROP DEFAULT;

-- 발송 작업은 PENDING 알림을 알림 ID 순으로 집는다. PENDING은 보내기 전 잠깐만 머무는 상태라 이 구간은 늘 작다.
CREATE INDEX idx_notification_email_status_id ON notification (email_status, id);
