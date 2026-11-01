-- 인증 코드 행을 발송 요청 이벤트(outbox_event) 하나에 하나만 두려고 이벤트 ID를 기록한다.
-- 메일 서버가 멈춰서 발행기가 같은 이벤트를 여러 번 다시 처리해도, 처리기는 이 값으로 같은 행을 찾아 코드만 새로 쓴다.
-- 이전에는 다시 처리할 때마다 행이 늘어서, 메일이 한 통도 나가지 않았는데 하루 재발송 한도와 IP 한도가 먼저 찼다.
--
-- 이벤트는 처리가 끝나면 지워지고, 가리키는 행이 지워진 뒤에도 처리해야 하므로 FK는 걸지 않는다(outbox_event와 같은 이유).
-- 이 컬럼이 생기기 전에 만든 행은 NULL이다. UNIQUE는 NULL 여러 개를 허용한다.
-- 유니크 인덱스는 같은 이벤트가 행을 둘 만들지 못하게 하고, 아직 코드 행이 없는 대기 중 이벤트를 찾는 조회(IP 한도 계산)도 받친다.
ALTER TABLE email_verification
    ADD COLUMN outbox_event_id BIGINT NULL AFTER member_id,
    ADD CONSTRAINT uk_email_verification_outbox_event UNIQUE (outbox_event_id);
