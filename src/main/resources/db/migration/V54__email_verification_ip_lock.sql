-- 같은 IP의 인증 메일 발송 요청(가입의 첫 발송과 재발송)을 한 줄로 세우는 잠금용 행이다. IP 하나가 한 행이다.
-- 서버는 IP별 시간당 발송 수를 email_verification 행과 대기 중인 outbox_event 행으로 세는데, 그 조회는 행을 잠그지 않는다.
-- 그래서 같은 IP에서 동시에 온 가입이 모두 같은 수를 읽고 통과해 시간당 한도 20건을 넘겼다. 서버가 세기 전에 이 행을 쓰기 잠금으로
-- 잡으면 같은 IP의 요청이 하나씩 세고 기록하므로 한도를 넘지 않는다. 이 행에는 횟수를 두지 않고, 횟수는 지금처럼 발송 기록에서 센다.
--
-- ip_key는 요청 IP의 SHA-256 해시다. 서버가 잠금 문장에서 SHA2로 만든다. 회원과 상관없는 값이라 member와 FK를 걸지 않는다.
-- last_requested_at이 하루 넘게 지난 행은 쓸모가 없어서 이메일 인증 기록 정리 작업이 지운다. 그 작업이 이 칼럼으로 지울 행을 찾는다.
CREATE TABLE email_verification_ip_lock (
    ip_key CHAR(64) NOT NULL,
    last_requested_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ip_key),
    INDEX idx_email_verification_ip_lock_last_requested_at (last_requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
