-- 관리자가 가입 차단 목록에 직접 더한 일회용 이메일 도메인이다(EV-04). 가입 검사는 이 표와 공개 목록(disposable_email_domain)을 함께 본다.
-- 공개 목록은 도메인이 기본 키라서 감사 로그의 target_id(BIGINT)로 가리킬 수 없다. 그래서 숫자 id를 가진 표를 따로 둔다.
-- 추가와 삭제를 누가 했는지는 admin_audit_log에 남는다.
CREATE TABLE admin_disposable_email_domain (
    id BIGINT NOT NULL AUTO_INCREMENT,
    domain VARCHAR(253) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_disposable_email_domain_domain UNIQUE (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
