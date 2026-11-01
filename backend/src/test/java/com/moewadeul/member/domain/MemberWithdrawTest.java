package com.moewadeul.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class MemberWithdrawTest {

    private static final String HASH = "$2a$10$" + "a".repeat(53);
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(5));

    @Test
    @DisplayName("[F-01][PV-01][PV-02][PV-03] 탈퇴하면 이메일·비밀번호 해시·자기 신고 값을 지우고 닉네임을 탈퇴회원_{id}로 바꾼다")
    void withdrawErasesPersonalData() {
        Member member = savedMember(12L);
        member.changeSelfAgeGroup(AgeGroup.TWENTIES, NOW);
        member.changeSelfGender(Gender.FEMALE, NOW);

        member.withdraw(LATER);

        assertThat(member.getEmail()).isNull();
        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getSelfAgeGroup()).isNull();
        assertThat(member.getSelfGender()).isNull();
        assertThat(member.getNickname()).isEqualTo("탈퇴회원_12");
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getWithdrawnAt()).isEqualTo(LATER);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("[PV-01] 이미 탈퇴한 회원에게 다시 탈퇴를 호출해도 탈퇴 시각이 바뀌지 않는다")
    void withdrawTwiceKeepsFirstWithdrawnAt() {
        Member member = savedMember(12L);
        member.withdraw(LATER);

        member.withdraw(LATER.plus(Duration.ofHours(1)));

        assertThat(member.getWithdrawnAt()).isEqualTo(LATER);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("탈퇴 시각이 null이면 IllegalArgumentException이다")
    void withdrawRejectsNullNow() {
        Member member = savedMember(12L);

        assertThatThrownBy(() -> member.withdraw(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"탈퇴회원_1", "탈퇴회원_12", "탈퇴회원_0123456789", "탈퇴회원_", "탈퇴회원_abc", "탈퇴회원_1a"})
    @DisplayName("[PV-02] 탈퇴회원_로 시작하는 닉네임은 가입과 닉네임 변경에서 입력 오류다")
    void reservedNicknameIsRejected(String nickname) {
        Member member = savedMember(1L);

        assertThat(Member.isValidNickname(nickname)).isFalse();
        assertInvalidInput(() -> Member.register(Email.of("a@example.com"), HASH, nickname, NOW));
        assertInvalidInput(() -> member.changeNickname(nickname, LATER));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "탈퇴회원_１２３",
                "\u1110\u1161\u11af\u1110\u116c\u1112\u116c\u110b\u116f\u11ab_123",
                "탈퇴회원＿123",
                "탈퇴회원_〡〢〣"
            })
    @DisplayName("[PV-02] 전각·조합형·호환 문자로 쓴 탈퇴회원_ 닉네임도 가입과 닉네임 변경에서 입력 오류다")
    void reservedNicknameWrittenWithLookalikeCharactersIsRejected(String nickname) {
        Member member = savedMember(1L);

        assertThat(Member.isValidNickname(nickname)).isFalse();
        assertInvalidInput(() -> Member.register(Email.of("a@example.com"), HASH, nickname, NOW));
        assertInvalidInput(() -> member.changeNickname(nickname, LATER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"탈퇴회원", "내탈퇴회원_1", "탈퇴회원 1", "탈퇴회원1", "탈퇴한산악인"})
    @DisplayName("[PV-02] 탈퇴회원_로 시작하지 않는 비슷한 닉네임은 쓸 수 있다")
    void similarNicknameIsAllowed(String nickname) {
        assertThat(Member.isValidNickname(nickname)).isTrue();
    }

    @Test
    @DisplayName("[PV-01] 탈퇴한 회원만 isWithdrawn이 참이다")
    void isWithdrawnOnlyAfterWithdraw() {
        Member member = savedMember(12L);
        assertThat(member.isWithdrawn()).isFalse();

        member.suspendPermanently(NOW);
        assertThat(member.isWithdrawn()).isFalse();

        member.withdraw(LATER);
        assertThat(member.isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("[PV-01] 비밀번호 인증은 탈퇴하지 않았고 비밀번호 해시가 있는 회원만 할 수 있다")
    void canAuthenticateWithPasswordNeedsLiveMemberWithHash() {
        Member member = savedMember(12L);
        assertThat(member.canAuthenticateWithPassword()).isTrue();

        ReflectionTestUtils.setField(member, "passwordHash", null);
        assertThat(member.canAuthenticateWithPassword()).isFalse();

        Member withdrawn = savedMember(13L);
        ReflectionTestUtils.setField(withdrawn, "status", MemberStatus.WITHDRAWN);
        assertThat(withdrawn.canAuthenticateWithPassword()).isFalse();
    }

    private static Member savedMember(long id) {
        Member member = Member.register(Email.of("User@Example.com"), HASH, "nick", NOW);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static void assertInvalidInput(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
