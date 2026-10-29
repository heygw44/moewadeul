package com.moewadeul.member.application.login;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemberLoginServiceIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String IP = "203.0.113.7";
    private static final int ATTEMPTED_EMAIL_MAX_LENGTH = 254;
    private static final int MAX_FAILURES = 5;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private MemberLoginService memberLoginService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-02] 이메일 형식이 올바르지 않아도 500이 아니라 LOGIN_FAILED로 거부하고 실패를 기록한다")
    void malformedEmailIsRejectedAsLoginFailed() {
        assertThatThrownBy(() -> memberLoginService.login(new LoginCommand("Not-An-Email", PASSWORD, IP)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.LOGIN_FAILED);

        Integer failures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_history WHERE attempted_email = ? AND member_id IS NULL AND success = FALSE",
                Integer.class,
                "not-an-email");
        assertThat(failures).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02] 이메일이 컬럼 길이보다 길면 잘라서 기록하고 LOGIN_FAILED로 거부한다")
    void overlongEmailIsTruncatedWhenRecorded() {
        String overlong = "x".repeat(ATTEMPTED_EMAIL_MAX_LENGTH + 46);

        assertThatThrownBy(() -> memberLoginService.login(new LoginCommand(overlong, PASSWORD, IP)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.LOGIN_FAILED);

        Integer recordedLength =
                jdbc.queryForObject("SELECT MAX(CHAR_LENGTH(attempted_email)) FROM login_history", Integer.class);
        assertThat(recordedLength).isEqualTo(ATTEMPTED_EMAIL_MAX_LENGTH);
    }

    @Test
    @DisplayName("[PW-03] 악센트만 다른 기존 계정 한쪽에서 5번 틀려도 다른 계정은 잠기지 않는다")
    void failuresOfAccentVariantAccountDoNotLockOtherAccount() {
        String localPart = "tomas-" + TestSequence.next();
        Member plain = memberFixture.saveWithPassword(aMember().email(localPart + "@example.com"), PASSWORD);
        Member accented = memberFixture.saveWithPassword(aMember().email(TestSequence.email()), PASSWORD);
        String accentedEmail = localPart.replace('a', 'á') + "@example.com";
        jdbc.update("UPDATE member SET email = ? WHERE id = ?", accentedEmail, accented.getId());

        for (int i = 0; i < MAX_FAILURES; i++) {
            assertThatThrownBy(() -> memberLoginService.login(new LoginCommand(accentedEmail, "Wrong-pass1!", IP)))
                    .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.LOGIN_FAILED);
        }

        assertThat(memberLoginService
                        .login(new LoginCommand(plain.getEmail(), PASSWORD, IP))
                        .memberId())
                .isEqualTo(plain.getId());
        assertThatThrownBy(() -> memberLoginService.login(new LoginCommand(accentedEmail, PASSWORD, IP)))
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.LOGIN_LOCKED);
    }
}
