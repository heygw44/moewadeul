-- 합류 신청의 대기 순서를 정하는 시각이다. 신청자가 취소한 뒤 다시 신청하면 서버는 같은 행을 대기로 되돌리는데,
-- 그동안 대기 목록이 처음 신청한 시각(created_at)으로 정렬돼서 다시 신청한 사람이 원래 자리로 끼어들었다.
-- 그래서 서버는 처음 신청할 때와 다시 신청할 때 모두 applied_at을 그 시각으로 바꾸고, 대기 목록을 이 컬럼으로 정렬한다.
-- 다시 신청한 사람은 대기 순서 맨 뒤로 간다.
-- 이미 있는 행은 다시 신청한 시각을 알 수 없으므로 처음 신청한 시각으로 채운다.
-- 대기 목록 조회는 basecamp_id로 시작하는 UNIQUE 인덱스로 행을 좁힌 뒤 정렬하고, 이전 정렬 기준(created_at)에도 인덱스가 없었으므로 인덱스를 따로 두지 않는다.
ALTER TABLE basecamp_application
    ADD COLUMN applied_at DATETIME(6) NULL AFTER decided_at;

UPDATE basecamp_application
SET applied_at = created_at;

ALTER TABLE basecamp_application
    MODIFY COLUMN applied_at DATETIME(6) NOT NULL;
