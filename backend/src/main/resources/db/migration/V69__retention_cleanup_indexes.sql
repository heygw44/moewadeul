-- 보관 기한이 지난 행을 지우는 정리 작업 다섯 개가 조건 컬럼에 인덱스가 없어 테이블 전체를 읽었다.
-- REPEATABLE READ에서 DELETE/UPDATE는 읽은 행마다 잠금을 걸므로, 인덱스가 없으면 LIMIT을 줘도 읽은 행 전부를 잠근다.
-- 정리가 끝날 때까지 같은 테이블의 INSERT가 기다린다. 아래 인덱스를 두면 조건에 맞는 구간만 읽고 잠근다.

-- EmailVerificationCleanupMapper.deleteOlderThan: WHERE created_at < ?
CREATE INDEX idx_email_verification_created_at ON email_verification (created_at);

-- PasswordResetTokenMapper.deleteExpiredBefore: WHERE expires_at <= ?
CREATE INDEX idx_password_reset_token_expires_at ON password_reset_token (expires_at);

-- CiRetentionCleanupMapper.deleteRetentionExpired: WHERE ci_retained_until IS NOT NULL AND ci_retained_until <= ?
-- 탈퇴하지 않은 회원은 NULL이라 인덱스에서 NULL 구간을 건너뛴다.
CREATE INDEX idx_identity_verification_ci_retained_until ON identity_verification (ci_retained_until);

-- OutboxEventCleanupMapper.deletePublishedBefore: WHERE status = 'PUBLISHED' AND published_at < ?
-- 발행기가 쓰는 idx_outbox_event_status_id (status, id)는 그대로 둔다.
CREATE INDEX idx_outbox_event_status_published_at ON outbox_event (status, published_at);

-- SanctionExpiryMapper.expireEnded: WHERE status = 'ACTIVE' AND ends_at IS NOT NULL AND ends_at <= ?
CREATE INDEX idx_sanction_status_ends_at ON sanction (status, ends_at);
