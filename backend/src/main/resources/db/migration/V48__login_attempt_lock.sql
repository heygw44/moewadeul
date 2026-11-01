-- 같은 이메일의 로그인 시도를 한 줄로 세우는 잠금용 행이다. 이메일 하나가 한 행이다.
-- 서버는 비밀번호를 비교하는 동안 DB 트랜잭션을 열지 않는다. 그래서 잠금 확인과 실패 기록 사이에 같은 이메일의 다른 요청이 끼어들어,
-- 요청 50건이 모두 "아직 실패 0번"으로 보고 통과할 수 있었다. 비교를 마친 뒤 이 행을 쓰기 잠금으로 잡고 잠금 여부를 다시 확인하면
-- 실패를 기록하는 요청이 한 번에 하나씩만 지나가서 10분에 5번을 넘지 못한다.
--
-- attempt_key는 원문 이메일이 아니라 SHA-256 해시다. 가입하지 않은 이메일로 시도해도 행이 생겨야 가입 여부가 드러나지 않으므로 member와 FK를 걸지 않는다.
-- last_attempt_at이 오래된 행은 쓸모가 없어서 로그인 기록 정리 작업이 지운다. 그 작업이 이 칼럼으로 지울 행을 찾는다.
CREATE TABLE login_attempt_lock (
    attempt_key CHAR(64) NOT NULL,
    last_attempt_at DATETIME(6) NOT NULL,
    PRIMARY KEY (attempt_key),
    INDEX idx_login_attempt_lock_last_attempt_at (last_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
