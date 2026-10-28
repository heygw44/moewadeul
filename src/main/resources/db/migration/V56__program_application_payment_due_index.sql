-- 결제 기한 만료 작업은 매분 결제 대기 신청 가운데 기한이 지난 것을 id 순서로 500건씩 찾는다.
-- 맞는 인덱스가 없어서 기본 키를 처음부터 훑었다(로컬 부하 DB 신청 42만 행에서 매번 42만 행 읽음).
-- (status, payment_due_at)이면 기한이 지난 결제 대기 신청만 읽고, 그 몇 건만 id로 정렬한다.
CREATE INDEX idx_program_application_status_payment_due ON program_application (status, payment_due_at);
