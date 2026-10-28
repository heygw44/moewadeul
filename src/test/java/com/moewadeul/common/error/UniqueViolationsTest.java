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

    private static DataIntegrityViolationException duplicate(String message) {
        return new DataIntegrityViolationException(
                "could not execute statement", new SQLIntegrityConstraintViolationException(message));
    }
}
