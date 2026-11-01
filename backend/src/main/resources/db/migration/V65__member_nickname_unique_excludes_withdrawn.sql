-- utf8mb4_0900_ai_ci가 무시하는 글자로 탈퇴회원_{id}와 같은 값을 먼저 쓴 회원이 있어도 탈퇴가 막히지 않게, 탈퇴 회원은 UNIQUE에서 뺀다.
-- 제약 이름은 MemberUniqueConstraintTranslator가 알아보도록 그대로 두고, 닉네임 중복 조회용 일반 인덱스를 남긴다.
ALTER TABLE member
    DROP INDEX uk_member_nickname,
    ADD COLUMN active_nickname VARCHAR(20) GENERATED ALWAYS AS (IF(status = 'WITHDRAWN', NULL, nickname)) VIRTUAL,
    ADD CONSTRAINT uk_member_nickname UNIQUE (active_nickname),
    ADD INDEX idx_member_nickname (nickname);
