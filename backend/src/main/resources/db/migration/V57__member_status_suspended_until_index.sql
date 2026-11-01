-- 정지 해제 작업은 10분마다 정지 기한이 지난 회원을 찾아 상태를 되돌린다.
-- 맞는 인덱스가 없어서 회원 표 전체를 읽었다(로컬 부하 DB 회원 23만 행에서 매번 23만 행 읽음).
-- (status, suspended_until)이면 기한이 지난 정지 회원만 읽는다.
CREATE INDEX idx_member_status_suspended_until ON member (status, suspended_until);
