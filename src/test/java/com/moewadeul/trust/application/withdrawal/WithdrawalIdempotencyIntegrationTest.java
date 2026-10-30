package com.moewadeul.trust.application.withdrawal;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.domain.Member;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class WithdrawalIdempotencyIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String IP = "203.0.113.9";

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private WithdrawalApplier withdrawalApplier;

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("[F-01] 탈퇴한 뒤 같은 요청이 한 번 더 오면 비밀번호를 묻지 않고 성공하며 정리 이벤트와 탈퇴 시각은 그대로다")
    void secondWithdrawalSucceedsWithoutSideEffects() {
        // given
        Member member = memberFixture.saveWithPassword(PASSWORD);
        withdrawalService.withdraw(member.getId(), PASSWORD, IP);
        LocalDateTime withdrawnAt = withdrawnAt(member);
        clock.advance(Duration.ofMinutes(1));

        // when, then
        assertThatCode(() -> withdrawalService.withdraw(member.getId(), PASSWORD, IP))
                .doesNotThrowAnyException();
        assertThat(cleanupEventCount(member)).isEqualTo(3);
        assertThat(withdrawnAt(member)).isEqualTo(withdrawnAt);
    }

    @Test
    @DisplayName("[F-01] 비밀번호 확인을 통과한 두 요청 중 늦은 쪽이 이미 탈퇴한 회원을 만나도 성공하고 정리 이벤트를 다시 쌓지 않는다")
    void lateApplyAfterConcurrentWithdrawalSucceeds() {
        // given
        Member member = memberFixture.saveWithPassword(PASSWORD);
        withdrawalApplier.apply(member.getId());

        // when, then
        assertThatCode(() -> withdrawalApplier.apply(member.getId())).doesNotThrowAnyException();
        assertThat(cleanupEventCount(member)).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-01][PW-08] 탈퇴가 커밋된 뒤 세션이 지워지기 전에 틀린 비밀번호로 탈퇴를 다시 보내도 204이고 로그인 실패로 기록하지 않는다")
    void withdrawnMemberWithWrongPasswordGetsNoContent() {
        // given
        Member member = memberFixture.saveWithPassword(PASSWORD);
        Cookie session = login(member);
        withdrawalApplier.apply(member.getId());

        // when
        MvcTestResult result = mvc.delete()
                .uri("/api/me")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Wrong-pass9\"}")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(cleanupEventCount(member)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM login_history WHERE member_id = ? AND success = FALSE",
                        Integer.class,
                        member.getId()))
                .isZero();
    }

    private Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return result.getResponse().getCookie("SESSION");
    }

    private int cleanupEventCount(Member member) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type LIKE 'WITHDRAWAL_%' AND aggregate_id = ?",
                Integer.class, member.getId());
    }

    private LocalDateTime withdrawnAt(Member member) {
        return jdbc.queryForObject("SELECT withdrawn_at FROM member WHERE id = ?", LocalDateTime.class, member.getId());
    }
}
