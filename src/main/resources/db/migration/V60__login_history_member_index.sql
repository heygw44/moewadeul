-- 회원을 찾은 로그인 시도는 그 회원의 최근 10분 실패와 마지막 성공을 회원 id로 센다. 외래 키 인덱스(member_id)만 있으면
-- 마지막 성공을 찾을 때 그 회원의 보관 기간 안 기록을 모두 읽는다. V43의 이메일 인덱스와 같은 모양으로 둔다.
CREATE INDEX idx_login_history_member_success_created ON login_history (member_id, success, created_at);
