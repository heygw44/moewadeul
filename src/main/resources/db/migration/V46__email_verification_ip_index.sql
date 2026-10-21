-- 인증 메일을 재발송할 때 서버는 회원 행을 잠근 뒤, 같은 IP가 최근 1시간에 발급받은 수를 센다.
-- request_ip 인덱스가 없으면 이 조회가 인증 기록 전체를 읽는다. 기록 100만 건(188MB)에서 MySQL 버퍼 풀이 다른 데이터로 차 있으면
-- 조회할 때마다 디스크 페이지 약 9,000개를 다시 읽어 재발송 한 번이 2.2~2.9초 걸렸고, 그동안 회원 행 잠금을 잡았다.

-- IP별로 최근 1시간 범위만 읽는다. 같은 IP의 행 중 시각이 범위 안인 것만 인덱스에서 세므로 기본 키로 행을 다시 찾지 않는다.
CREATE INDEX idx_email_verification_ip_created ON email_verification (request_ip, created_at);
