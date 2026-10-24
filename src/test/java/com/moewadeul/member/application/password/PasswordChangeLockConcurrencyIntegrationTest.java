package com.moewadeul.member.application.password;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로그인한 회원이 현재 비밀번호를 틀린 채 변경 요청을 겹쳐 보내도, 로그인 실패와 합쳐 10분 안에 5번까지만 틀릴 수 있는지 확인한다.
 */
@IntegrationTest
class PasswordChangeLockConcurrencyIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String NEW_PASSWORD = "Newer-pass2";
    private static final String IP = "203.0.113.7";
    private static final int BURST_SIZE = 20;
    private static final int MAX_FAILURES = 5;
    private static final long WAIT_SECONDS = 60;

    @Autowired
    private PasswordChangeService passwordChangeService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(3)
    @DisplayName("[PW-07][PW-03] 현재 비밀번호를 틀린 변경 요청이 동시에 와도 실패는 5번만 기록하고 나머지는 LOGIN_LOCKED로 거부한다")
    void burstOfWrongCurrentPasswordsRecordsOnlyFiveFailures() throws Exception {
        Member member = memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .build());

        List<MemberErrorCode> outcomes = changeTogether(member.getId());

        Integer failures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_history WHERE attempted_email = ? AND success = FALSE",
                Integer.class,
                member.getEmail());
        assertThat(failures).isEqualTo(MAX_FAILURES);
        assertThat(outcomes.stream().filter(code -> code == MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH))
                .hasSize(MAX_FAILURES);
        assertThat(outcomes.stream().filter(code -> code == MemberErrorCode.LOGIN_LOCKED))
                .hasSize(BURST_SIZE - MAX_FAILURES);
    }

    // 요청 BURST_SIZE건을 한꺼번에 출발시키고 각 요청이 던진 오류 코드를 모은다. 모두 틀린 현재 비밀번호라서 성공하는 요청은 없어야 한다.
    private List<MemberErrorCode> changeTogether(long memberId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(BURST_SIZE);
        CountDownLatch ready = new CountDownLatch(BURST_SIZE);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < BURST_SIZE; i++) {
                String wrongPassword = "Wrong-pass" + i + "!";
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    passwordChangeService.change(
                            new PasswordChangeCommand(memberId, wrongPassword, NEW_PASSWORD, IP, "session"));
                    return null;
                }));
            }
            assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<MemberErrorCode> outcomes = new ArrayList<>();
            for (Future<?> future : futures) {
                outcomes.add(errorCodeOf(future));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private static MemberErrorCode errorCodeOf(Future<?> future) throws InterruptedException {
        try {
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
            return (MemberErrorCode) ((BusinessException) e.getCause()).getErrorCode();
        } catch (TimeoutException e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("요청이 예외 없이 끝났다");
    }
}
