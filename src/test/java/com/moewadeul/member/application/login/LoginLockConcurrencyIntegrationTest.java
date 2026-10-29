package com.moewadeul.member.application.login;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.PasswordMatchPause;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
@Import(PasswordMatchPause.Config.class)
class LoginLockConcurrencyIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String IP = "203.0.113.7";
    private static final int BURST_SIZE = 20;
    private static final int MAX_FAILURES = 5;
    private static final long WAIT_SECONDS = 60;

    @Autowired
    private MemberLoginService memberLoginService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearPause() {
        PasswordMatchPause.clear();
    }

    @RepeatedTest(3)
    @DisplayName("[PW-03] 같은 이메일에 서로 다른 틀린 비밀번호로 동시에 로그인해도 실패는 5번만 기록하고 나머지는 LOGIN_LOCKED로 거부한다")
    void burstOfWrongPasswordsRecordsOnlyFiveFailures() throws Exception {
        Member member = saveMember();

        List<Throwable> outcomes = loginTogether(Collections.nCopies(BURST_SIZE, member.getEmail()));

        assertThat(failureCount(member.getEmail())).isEqualTo(MAX_FAILURES);
        assertThat(outcomes.stream().filter(e -> codeOf(e) == MemberErrorCode.LOGIN_FAILED))
                .hasSize(MAX_FAILURES);
        assertThat(outcomes.stream().filter(e -> codeOf(e) == MemberErrorCode.LOGIN_LOCKED))
                .hasSize(BURST_SIZE - MAX_FAILURES);
    }

    @RepeatedTest(3)
    @DisplayName("[PW-03] DB가 같은 이메일로 보는 표기 변형(보이지 않는 문자)으로 동시에 로그인해도 그 회원의 실패는 5번만 기록한다")
    void burstOfEmailVariantsRecordsOnlyFiveFailures() throws Exception {
        Member member = saveMember();
        List<String> variants = IntStream.range(0, BURST_SIZE)
                .mapToObj(i -> withZeroWidthSpaces(member.getEmail(), i))
                .toList();

        List<Throwable> outcomes = loginTogether(variants);

        assertThat(memberFailureCount(member.getId())).isEqualTo(MAX_FAILURES);
        assertThat(outcomes.stream().filter(e -> codeOf(e) == MemberErrorCode.LOGIN_FAILED))
                .hasSize(MAX_FAILURES);
        assertThat(outcomes.stream().filter(e -> codeOf(e) == MemberErrorCode.LOGIN_LOCKED))
                .hasSize(BURST_SIZE - MAX_FAILURES);
    }

    @Test
    @DisplayName("[PW-03] 잠기기 전에 시작한 올바른 비밀번호 요청은 비교가 잠긴 뒤에 끝나도 LOGIN_LOCKED로 거부하고 성공을 기록하지 않는다")
    void correctPasswordFinishingAfterLockIsRejected() throws Exception {
        Member member = saveMember();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PasswordMatchPause.arm(PASSWORD);
            Future<?> slowLogin =
                    executor.submit(() -> memberLoginService.login(new LoginCommand(member.getEmail(), PASSWORD, IP)));
            PasswordMatchPause.awaitPaused();
            for (int i = 0; i < MAX_FAILURES; i++) {
                assertThat(loginWithWrongPassword(member.getEmail(), i)).isEqualTo(MemberErrorCode.LOGIN_FAILED);
            }

            PasswordMatchPause.release();

            assertThat(codeOf(failureOf(slowLogin))).isEqualTo(MemberErrorCode.LOGIN_LOCKED);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM login_history WHERE attempted_email = ? AND success = TRUE",
                            Integer.class,
                            member.getEmail()))
                    .isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Throwable> loginTogether(List<String> emails) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(emails.size());
        CountDownLatch ready = new CountDownLatch(emails.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < emails.size(); i++) {
                String email = emails.get(i);
                String wrongPassword = wrongPassword(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return memberLoginService.login(new LoginCommand(email, wrongPassword, IP));
                }));
            }
            assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<?> future : futures) {
                outcomes.add(failureOf(future));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private MemberErrorCode loginWithWrongPassword(String email, int index) {
        try {
            memberLoginService.login(new LoginCommand(email, wrongPassword(index), IP));
            throw new AssertionError("틀린 비밀번호로 로그인되면 안 된다");
        } catch (BusinessException e) {
            return codeOf(e);
        }
    }

    private static String withZeroWidthSpaces(String email, int count) {
        int at = email.indexOf('@');
        return email.substring(0, at) + "\u200B".repeat(count) + email.substring(at);
    }

    private static String wrongPassword(int index) {
        return "Wrong-pass" + index + "!";
    }

    private static Throwable failureOf(Future<?> future) throws InterruptedException {
        try {
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (TimeoutException e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("요청이 예외 없이 끝났다");
    }

    private static MemberErrorCode codeOf(Throwable failure) {
        assertThat(failure).isInstanceOf(BusinessException.class);
        return (MemberErrorCode) ((BusinessException) failure).getErrorCode();
    }

    private int failureCount(String email) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_history WHERE attempted_email = ? AND success = FALSE",
                Integer.class,
                email);
    }

    private int memberFailureCount(long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_history WHERE member_id = ? AND success = FALSE", Integer.class, memberId);
    }

    private Member saveMember() {
        return memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .build());
    }
}
