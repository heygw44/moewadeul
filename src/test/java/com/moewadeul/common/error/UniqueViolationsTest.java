package com.moewadeul.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UniqueViolationsTest {

    private static final String EMAIL_KEY = "uk_member_email";
    private static final String NICKNAME_KEY = "uk_member_nickname";

    @Test
    @DisplayName("마지막 for key 뒤의 제약 이름이 같으면 위반으로 본다")
    void matchesViolatedConstraint() {
        // given
        DataIntegrityViolationException e = duplicate("Duplicate entry 'a@b.com' for key 'member.uk_member_email'");

        // when
        boolean violated = UniqueViolations.violates(e, EMAIL_KEY);

        // then
        assertThat(violated).isTrue();
    }

    @Test
    @DisplayName("다른 제약이 깨졌으면 위반으로 보지 않는다")
    void ignoresOtherConstraint() {
        // given
        DataIntegrityViolationException e = duplicate("Duplicate entry 'a@b.com' for key 'member.uk_member_email'");

        // when
        boolean violated = UniqueViolations.violates(e, NICKNAME_KEY);

        // then
        assertThat(violated).isFalse();
    }

    @Test
    @DisplayName("중복된 값 자리에 다른 제약 이름이 들어 있어도 값은 보지 않는다")
    void ignoresConstraintNameInsideDuplicatedValue() {
        // given
        DataIntegrityViolationException e =
                duplicate("Duplicate entry 'uk_member_email' for key 'member.uk_member_nickname'");

        // when
        boolean violatesEmail = UniqueViolations.violates(e, EMAIL_KEY);
        boolean violatesNickname = UniqueViolations.violates(e, NICKNAME_KEY);

        // then
        assertThat(violatesEmail).isFalse();
        assertThat(violatesNickname).isTrue();
    }

    @Test
    @DisplayName("값에 for key 문구가 들어 있어도 마지막 for key 뒤만 본다")
    void usesLastKeyMarker() {
        // given
        DataIntegrityViolationException e =
                duplicate("Duplicate entry 'x for key 'uk_member_email' y' for key 'member.uk_member_nickname'");

        // when
        boolean violatesEmail = UniqueViolations.violates(e, EMAIL_KEY);

        // then
        assertThat(violatesEmail).isFalse();
    }

    @Test
    @DisplayName("for key 문구가 없으면 메시지 전체에서 제약 이름을 찾는다")
    void searchesWholeMessageWithoutMarker() {
        // given
        DataIntegrityViolationException e = duplicate("constraint uk_member_email violated");

        // when
        boolean violated = UniqueViolations.violates(e, EMAIL_KEY);

        // then
        assertThat(violated).isTrue();
    }

    @Test
    @DisplayName("원인 메시지가 없으면 위반으로 보지 않는다")
    void returnsFalseWithoutMessage() {
        // given
        DataIntegrityViolationException e =
                new DataIntegrityViolationException("wrapper", new RuntimeException((String) null));

        // when
        boolean violated = UniqueViolations.violates(e, EMAIL_KEY);

        // then
        assertThat(violated).isFalse();
    }

    @Test
    @DisplayName("[translate] 어긴 제약이 같으면 지정한 오류 코드의 BusinessException으로 바꾼다")
    void translateConvertsMatchingConstraint() {
        // given
        DataIntegrityViolationException e = duplicate("Duplicate entry 'a@b.com' for key 'member.uk_member_email'");

        // when
        RuntimeException translated = UniqueViolations.translate(e, EMAIL_KEY, CommonErrorCode.INVALID_INPUT);

        // then
        assertThat(translated)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        be -> assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("[translate] 다른 유니크 제약을 어겼으면 원래 예외를 그대로 돌려준다")
    void translateKeepsOtherUniqueViolation() {
        // given
        DataIntegrityViolationException e = duplicate("Duplicate entry 'x' for key 'member.uk_member_nickname'");

        // when
        RuntimeException translated = UniqueViolations.translate(e, EMAIL_KEY, CommonErrorCode.INVALID_INPUT);

        // then
        assertThat(translated).isSameAs(e);
    }

    @Test
    @DisplayName("[translate] 외래 키 위반은 중복으로 바꾸지 않고 원래 예외를 그대로 돌려준다")
    void translateKeepsForeignKeyViolation() {
        // given
        DataIntegrityViolationException e = duplicate(
                "Cannot add or update a child row: a foreign key constraint fails (`moewadeul`.`spot_review`, "
                        + "CONSTRAINT `fk_spot_review_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`))");

        // when
        RuntimeException translated =
                UniqueViolations.translate(e, "uk_spot_review_spot_member_visited_date", CommonErrorCode.INVALID_INPUT);

        // then
        assertThat(translated).isSameAs(e);
    }

    @Test
    @DisplayName("[translate] NOT NULL 위반은 중복으로 바꾸지 않고 원래 예외를 그대로 돌려준다")
    void translateKeepsNotNullViolation() {
        // given
        DataIntegrityViolationException e = duplicate("Column 'content' cannot be null");

        // when
        RuntimeException translated = UniqueViolations.translate(e, EMAIL_KEY, CommonErrorCode.INVALID_INPUT);

        // then
        assertThat(translated).isSameAs(e);
    }

    @Test
    @DisplayName("[redactedMessage] 중복 메시지에서 값은 빼고 제약 이름만 남긴다")
    void redactedMessageDropsDuplicatedValue() {
        // given
        DataIntegrityViolationException e =
                duplicate("Duplicate entry 'secret@example.com' for key 'member.uk_member_email'");

        // when
        String described = UniqueViolations.redactedMessage(e);

        // then
        assertThat(described).isEqualTo("Duplicate entry for key 'member.uk_member_email'");
    }

    @Test
    @DisplayName("[isDuplicateEntry] MySQL 오류 코드가 1062이면 고유 제약 위반으로 본다")
    void isDuplicateEntryForErrorCode1062() {
        // given
        DataIntegrityViolationException e = withErrorCode("Duplicate entry 'x' for key 'member.uk_member_email'", 1062);

        // when
        boolean duplicateEntry = UniqueViolations.isDuplicateEntry(e);

        // then
        assertThat(duplicateEntry).isTrue();
    }

    @Test
    @DisplayName("[isDuplicateEntry] NOT NULL·외래 키 위반은 고유 제약 위반으로 보지 않는다")
    void isNotDuplicateEntryForOtherErrorCodes() {
        // given
        DataIntegrityViolationException notNull = withErrorCode("Column 'content' cannot be null", 1048);
        DataIntegrityViolationException foreignKey =
                withErrorCode("Cannot add or update a child row: a foreign key constraint fails", 1452);

        // when
        boolean notNullDuplicate = UniqueViolations.isDuplicateEntry(notNull);
        boolean foreignKeyDuplicate = UniqueViolations.isDuplicateEntry(foreignKey);

        // then
        assertThat(notNullDuplicate).isFalse();
        assertThat(foreignKeyDuplicate).isFalse();
    }

    private static DataIntegrityViolationException duplicate(String message) {
        return new DataIntegrityViolationException(
                "could not execute statement", new SQLIntegrityConstraintViolationException(message));
    }

    private static DataIntegrityViolationException withErrorCode(String message, int errorCode) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException(message, "23000", errorCode));
    }
}
