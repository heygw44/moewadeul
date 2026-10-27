package com.moewadeul.member.application.signup;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.EmailVerificationEvents;
import com.moewadeul.member.application.verification.EmailVerificationService;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberException;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemberSignupConcurrencyIntegrationTest {

    private static final int THREADS = 10;
    private static final String VALID_PASSWORD = "Passw0rd!xyz";
    private static final String REQUEST_IP = "203.0.113.7";
    private static final int IP_HOURLY_LIMIT = 20;

    @Autowired
    MemberSignupService memberSignupService;

    @Autowired
    MemberJpaRepository memberJpaRepository;

    @Autowired
    EmailVerificationService emailVerificationService;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-01][EV-06] 같은 이메일로 동시에 가입하면 1건만 성공하고 나머지는 MEMBER_EMAIL_DUPLICATED")
    void onlyOneSignupSucceedsForSameEmail() throws Exception {
        // given
        String email = TestSequence.email();

        // when
        List<Throwable> failures = signUpConcurrently(
                THREADS, i -> new SignupCommand(email, VALID_PASSWORD, TestSequence.nickname(), REQUEST_IP));

        // then
        assertOnlyDuplicateFailures(failures, MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        assertThat(memberJpaRepository.count()).isEqualTo(1);
        assertThat(memberJpaRepository.existsByEmail(email)).isTrue();
    }

    @RepeatedTest(5)
    @DisplayName("[F-01] 같은 닉네임으로 동시에 가입하면 1건만 성공하고 나머지는 MEMBER_NICKNAME_DUPLICATED")
    void onlyOneSignupSucceedsForSameNickname() throws Exception {
        // given
        String nickname = TestSequence.nickname();

        // when
        List<Throwable> failures = signUpConcurrently(
                THREADS, i -> new SignupCommand(TestSequence.email(), VALID_PASSWORD, nickname, REQUEST_IP));

        // then
        assertOnlyDuplicateFailures(failures, MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        assertThat(memberJpaRepository.count()).isEqualTo(1);
        assertThat(memberJpaRepository.existsByNickname(nickname)).isTrue();
    }

    @RepeatedTest(5)
    @DisplayName("[F-01][EV-03] 한 시간에 인증 메일을 19건 요청한 IP에서 가입 10건이 동시에 오면 1건만 성공하고 나머지는 SIGNUP_LIMITED다")
    void onlyOneSignupSucceedsWhenIpIsOneBelowHourlyLimit() throws Exception {
        // given
        insertQueuedSends(REQUEST_IP, IP_HOURLY_LIMIT - 1);

        // when
        List<Throwable> failures = signUpConcurrently(THREADS, i -> signupFrom(REQUEST_IP));

        // then
        assertOnlyLimitedFailures(failures, THREADS - 1);
        assertThat(sendsInHour(REQUEST_IP)).isEqualTo(IP_HOURLY_LIMIT);
    }

    @RepeatedTest(3)
    @DisplayName("[F-01][EV-03] 처음 보는 IP에서 가입 30건이 동시에 오면 한 시간 한도인 20건만 성공하고 나머지는 SIGNUP_LIMITED다")
    void signupsFromOneIpNeverExceedHourlyLimit() throws Exception {
        // given
        int requests = 30;

        // when
        List<Throwable> failures = signUpConcurrently(requests, i -> signupFrom(REQUEST_IP));

        // then
        assertOnlyLimitedFailures(failures, requests - IP_HOURLY_LIMIT);
        assertThat(sendsInHour(REQUEST_IP)).isEqualTo(IP_HOURLY_LIMIT);
    }

    @RepeatedTest(5)
    @DisplayName("[F-01][EV-03] 한 시간에 인증 메일을 19건 요청한 IP에서 가입 5건과 다른 회원의 재발송 5건이 동시에 오면 1건만 성공하고 그 IP의 발송은 20건이다")
    void signupsAndResendsFromOneIpShareHourlyLimit() throws Exception {
        // given
        insertQueuedSends(REQUEST_IP, IP_HOURLY_LIMIT - 1);
        List<Callable<?>> requests = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            SignupCommand command = signupFrom(REQUEST_IP);
            requests.add(() -> memberSignupService.signUp(command));
        }
        for (int i = 0; i < 5; i++) {
            long memberId = memberJpaRepository.saveAndFlush(aMember().build()).getId();
            requests.add(() -> {
                emailVerificationService.requestResend(memberId, REQUEST_IP);
                return null;
            });
        }

        // when
        List<Throwable> failures = runConcurrently(requests);

        // then
        assertThat(failures)
                .hasSize(requests.size() - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                RateLimitedException.class,
                                e -> assertThat(e.getErrorCode())
                                        .isIn(MemberErrorCode.SIGNUP_LIMITED, MemberErrorCode.EMAIL_RESEND_LIMITED)));
        assertThat(sendsInHour(REQUEST_IP)).isEqualTo(IP_HOURLY_LIMIT);
    }

    private static SignupCommand signupFrom(String requestIp) {
        return new SignupCommand(TestSequence.email(), VALID_PASSWORD, TestSequence.nickname(), requestIp);
    }

    private static void assertOnlyLimitedFailures(List<Throwable> failures, int expectedCount) {
        assertThat(failures)
                .hasSize(expectedCount)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                RateLimitedException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SIGNUP_LIMITED)));
    }

    // 메일 서버가 멈춰 재시도를 기다리는 발송 요청을 넣는다. 다음 시도 시각이 하루 뒤라서 발행기가 집지 않고, 서버는 이 요청도 IP의 발송 수에 센다.
    private void insertQueuedSends(String requestIp, int count) {
        Timestamp now = Timestamp.from(clock.instant());
        Timestamp nextAttempt = Timestamp.from(clock.instant().plus(Duration.ofDays(1)));
        for (int i = 0; i < count; i++) {
            long memberId = memberJpaRepository.saveAndFlush(aMember().build()).getId();
            jdbc.update(
                    "INSERT INTO outbox_event (event_type, aggregate_type, aggregate_id, payload, status,"
                            + " attempt_count, locked_until, created_at, updated_at)"
                            + " VALUES (?, ?, ?, JSON_OBJECT('requestIp', ?), 'PENDING', 3, ?, ?, ?)",
                    EmailVerificationEvents.EVENT_TYPE,
                    EmailVerificationEvents.AGGREGATE_TYPE,
                    memberId,
                    requestIp,
                    nextAttempt,
                    now,
                    now);
        }
    }

    // 서버의 IP 한도와 같은 기준으로, 최근 한 시간에 그 IP가 요청한 인증 메일 발송 요청 수를 센다. 발행기가 아직 처리하지 않은 요청도 센다.
    private int sendsInHour(String requestIp) {
        Timestamp since = Timestamp.from(clock.instant().minus(Duration.ofHours(1)));
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND created_at > ?"
                        + " AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.requestIp')) = ?",
                Integer.class,
                EmailVerificationEvents.EVENT_TYPE,
                since,
                requestIp);
    }

    private void assertOnlyDuplicateFailures(List<Throwable> failures, MemberErrorCode expected) {
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                MemberException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(expected)));
    }

    private List<Throwable> signUpConcurrently(int threads, IntFunction<SignupCommand> commandFactory)
            throws Exception {
        List<Callable<?>> requests = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            SignupCommand command = commandFactory.apply(i);
            requests.add(() -> memberSignupService.signUp(command));
        }
        return runConcurrently(requests);
    }

    private static List<Throwable> runConcurrently(List<Callable<?>> requests) throws Exception {
        int threads = requests.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return request.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static List<Throwable> collectFailures(List<Future<?>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (java.util.concurrent.TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }
}
