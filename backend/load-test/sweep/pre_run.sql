-- 대응표와 측정 앞에 넣는 상태. 준비 단계의 앱이 쓴 메일 한도와 결제 기한 지난 신청을 되돌리고, 작업이 고를 행을 남긴다.
UPDATE notification SET email_sent_at = UTC_TIMESTAMP() - INTERVAL 3 DAY WHERE email_sent_at > UTC_TIMESTAMP() - INTERVAL 3 DAY;
UPDATE program_application SET status='PENDING_PAYMENT', cancel_reason=NULL, canceled_at=NULL, payment_due_at=UTC_TIMESTAMP() - INTERVAL 1 MINUTE
 WHERE program_id=(SELECT id FROM program WHERE title='sweep-open-0') AND member_id IN (SELECT id FROM member WHERE email LIKE 'bg0060%');
