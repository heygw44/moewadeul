-- 이메일이나 닉네임이 이미 쓰여서 거절한 가입 시도를 한 건에 한 행으로 남긴다.
-- 같은 IP의 시간당 한도는 인증 메일 발송 요청으로만 세는데, 중복으로 거절한 가입은 발송 요청을 만들지 않는다.
-- 그래서 한 IP가 가입 요청으로 어떤 이메일이 가입돼 있는지 한도 없이 확인할 수 있었다(#708). 이 행도 같은 한도에 센다.
--
-- ip_key는 요청 IP의 SHA-256 해시다. 서버가 INSERT와 조회 문장에서 SHA2로 만든다.
-- 한 시간이 지난 행은 한도 계산에 쓰지 않으므로 이메일 인증 기록 정리 작업이 attempted_at으로 찾아 지운다.
CREATE TABLE signup_duplicate_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ip_key CHAR(64) NOT NULL,
    attempted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_signup_duplicate_attempt_ip_key_attempted_at (ip_key, attempted_at),
    INDEX idx_signup_duplicate_attempt_attempted_at (attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
