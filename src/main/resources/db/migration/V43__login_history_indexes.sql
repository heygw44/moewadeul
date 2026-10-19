-- 로그인할 때 서버는 입력한 이메일의 최근 10분 실패와 마지막 성공을 세어 잠금 여부를 정한다.
-- 인덱스가 없으면 이 조회가 로그인 기록 전체를 읽어서, 기록 300만 건에서 로그인 한 번이 0.4~0.7초, 디스크가 느리면 2~6초 걸렸다.
-- 시각까지 넣어서 실패는 최근 10분 범위만, 성공은 그 이메일의 성공 항목만 읽는다. (이메일, 성공 여부, id)는 성공이 한 번도 없는
-- 이메일에서 보관 기간 안의 실패를 모두 읽어야 해서 고르지 않았다.
CREATE INDEX idx_login_history_email_success_created ON login_history (attempted_email, success, created_at);

-- 정리 작업이 3개월 지난 기록을 1,000건씩 지울 때 쓴다. 인덱스가 없으면 배치마다 표를 앞에서부터 읽는다.
CREATE INDEX idx_login_history_created ON login_history (created_at);
