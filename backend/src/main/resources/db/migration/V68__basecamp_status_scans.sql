-- 모집 중 목록(status = 'RECRUITING' ORDER BY start_date, id)과 출발·완료 자동 전이는 status로 거르지만 status 인덱스가 없어 30만 행을 모두 읽었다.
-- (status, start_date)로 거르기와 정렬을 인덱스 하나로 끝낸다.
CREATE INDEX idx_basecamp_status_start_date ON basecamp (status, start_date);

-- 연락 수단 정리는 contact_info가 NULL이 아닌 행만 필요하다. 이미 지운 행이 대부분이라 앞 한 글자만 담아 인덱스를 작게 둔다.
CREATE INDEX idx_basecamp_contact_info ON basecamp (contact_info(1));
