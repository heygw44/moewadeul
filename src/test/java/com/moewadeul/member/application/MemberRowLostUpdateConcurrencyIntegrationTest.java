package com.moewadeul.member.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.patch.PatchField;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.application.password.PasswordChangeCommand;
import com.moewadeul.member.application.password.PasswordChangeService;
import com.moewadeul.member.application.password.PasswordResetConfirmService;
import com.moewadeul.member.application.profile.MyInfoService;
import com.moewadeul.member.application.profile.MyInfoUpdateCommand;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberErrorCode;
import com.moewadeul.member.domain.MemberRepository;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.password.PasswordResetToken;
import com.moewadeul.member.domain.password.PasswordResetTokenRepository;
import com.moewadeul.member.domain.password.ResetToken;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportActionCommand;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@IntegrationTest
class MemberRowLostUpdateConcurrencyIntegrationTest {

    private static final long MIDDLE_WAIT_SECONDS = 2;
    private static final String OLD_PASSWORD = "Old-pass12";
    private static final String NEW_PASSWORD = "Newer-pass1";
    private static final String RESET_PASSWORD = "Reset-pass1";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    private static final Set<String> MEMBER_READ =
            Set.of("MemberRepository.findById", "MemberRepository.findByIdForUpdate");

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordChangeService passwordChangeService;

    @Autowired
    private PasswordResetConfirmService passwordResetConfirmService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private MyInfoService myInfoService;

    @Autowired
    private MemberReportActionService reportActionService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private final AtomicBoolean pauseArmed = new AtomicBoolean();
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        PauseHooks.clear();
        resume.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("[PW-07][SN-04] 비밀번호를 바꾸는 사이 관리자가 정지를 확정하면, 회원은 SUSPENDED로 남는다")
    void passwordChangeDuringSuspensionKeepsSuspended() throws Exception {
        Member target = activeMember();
        long adminId = memberFixture.save().getId();
        long reportId = inReviewReportAgainst(target.getId());
        pauseInsideTransactionAfter(MEMBER_READ);

        Future<?> change = executor.submit(() -> passwordChangeService.change(
                new PasswordChangeCommand(target.getId(), OLD_PASSWORD, NEW_PASSWORD, "203.0.113.7", "none")));
        awaitPaused();
        Throwable failure = runInMiddle(
                () -> reportActionService.act(
                        new MemberReportActionCommand(reportId, adminId, "PERMANENT", "사유", false, "메모")),
                change);

        assertThat(failure).isNull();
        assertThat(columnOf(target.getId(), "status")).isEqualTo("SUSPENDED");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, columnOf(target.getId(), "password_hash")))
                .isTrue();
    }

    @Test
    @DisplayName("[PW-02] 내 정보를 고치는 사이 비밀번호 재설정이 끝나면, 새 비밀번호와 새 닉네임이 함께 남는다")
    void myInfoUpdateDuringPasswordResetKeepsNewPassword() throws Exception {
        Member member = activeMember();
        String rawToken = ResetToken.generate(new SecureRandom()).value();
        tokenRepository.save(PasswordResetToken.issue(member.getId(), ResetToken.hash(rawToken), clock.instant()));
        String nickname = "새닉" + member.getId();
        pauseInsideTransactionAfter(MEMBER_READ);

        Future<?> update = executor.submit(() -> myInfoService.update(
                member.getId(),
                new MyInfoUpdateCommand(PatchField.of(nickname), PatchField.absent(), PatchField.absent())));
        awaitPaused();
        Throwable failure = runInMiddle(
                () -> {
                    passwordResetConfirmService.confirm(rawToken, NEW_PASSWORD);
                    return null;
                },
                update);

        assertThat(failure).isNull();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, columnOf(member.getId(), "password_hash")))
                .isTrue();
        assertThat(columnOf(member.getId(), "nickname")).isEqualTo(nickname);
    }

    @Test
    @DisplayName("[PW-07][F-01] 비밀번호 변경이 회원을 읽은 뒤 탈퇴가 커밋되면, 탈퇴한 회원 행에 비밀번호가 되살아나지 않는다")
    void passwordChangeAfterWithdrawalLeavesNoPassword() throws Exception {
        Member member = activeMember();
        pauseOutsideTransactionAfter(MEMBER_READ);

        Future<?> change = executor.submit(() -> passwordChangeService.change(
                new PasswordChangeCommand(member.getId(), OLD_PASSWORD, NEW_PASSWORD, "203.0.113.7", "none")));
        awaitPaused();
        Throwable failure = runInMiddle(
                () -> {
                    withdrawalService.withdraw(member.getId(), OLD_PASSWORD, "203.0.113.8");
                    return null;
                },
                change);

        assertThat(columnOf(member.getId(), "status")).isEqualTo("WITHDRAWN");
        assertThat(columnOf(member.getId(), "password_hash")).isNull();
        assertThat(failure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH));
    }

    @Test
    @DisplayName("[PW-07][PW-02] 비밀번호 변경이 현재 비밀번호를 확인한 뒤 재설정이 커밋되면, 변경은 거절되고 재설정한 비밀번호가 남는다")
    void passwordChangeAfterResetKeepsResetPassword() throws Exception {
        Member member = activeMember();
        String rawToken = ResetToken.generate(new SecureRandom()).value();
        tokenRepository.save(PasswordResetToken.issue(member.getId(), ResetToken.hash(rawToken), clock.instant()));
        pauseOutsideTransactionAfter(MEMBER_READ);

        Future<?> change = executor.submit(() -> passwordChangeService.change(
                new PasswordChangeCommand(member.getId(), OLD_PASSWORD, NEW_PASSWORD, "203.0.113.7", "none")));
        awaitPaused();
        Throwable failure = runInMiddle(
                () -> {
                    passwordResetConfirmService.confirm(rawToken, RESET_PASSWORD);
                    return null;
                },
                change);

        assertThat(failure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_CURRENT_PASSWORD_MISMATCH));
        assertThat(passwordEncoder.matches(RESET_PASSWORD, columnOf(member.getId(), "password_hash")))
                .isTrue();
    }

    private Member activeMember() {
        return memberFixture.saveWithPassword(
                aMember().status(MemberStatus.ACTIVE).emailVerifiedAt(BASE), OLD_PASSWORD);
    }

    private long inReviewReportAgainst(long targetId) {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        long basecampId = fixture.saveBasecamp("COMPLETED", BASE);
        long reporterId = memberFixture.save().getId();
        return new MemberReportFixture(jdbc)
                .insertMemberReport(reporterId, targetId, basecampId, "NO_SHOW", "IN_REVIEW", BASE);
    }

    private String columnOf(long memberId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM member WHERE id = ?", String.class, memberId);
    }

    private void pauseInsideTransactionAfter(Set<String> calls) {
        pauseArmed.set(true);
        PauseHooks.afterCalls = calls;
        PauseHooks.action = this::pauseOnceInsideTransaction;
    }

    private void pauseOutsideTransactionAfter(Set<String> calls) {
        pauseArmed.set(true);
        PauseHooks.afterCalls = calls;
        PauseHooks.action = this::pauseOnceOutsideTransaction;
    }

    private void pauseOnceInsideTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !pauseArmed.compareAndSet(true, false)) {
            return;
        }
        blockUntilResumed();
    }

    private void pauseOnceOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive() || !pauseArmed.compareAndSet(true, false)) {
            return;
        }
        blockUntilResumed();
    }

    private void blockUntilResumed() {
        paused.countDown();
        try {
            assertThat(resume.await(60, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void awaitPaused() throws InterruptedException {
        assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private Throwable runInMiddle(Callable<?> middle, Future<?> pausedWork) throws Exception {
        Future<?> middleWork = executor.submit(middle);
        try {
            middleWork.get(MIDDLE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
        }
        resume.countDown();
        middleWork.get(60, TimeUnit.SECONDS);
        try {
            pausedWork.get(60, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    static final class PauseHooks {

        static volatile Set<String> afterCalls = Set.of();
        static volatile Runnable action;

        private PauseHooks() {}

        static void clear() {
            afterCalls = Set.of();
            action = null;
        }

        private static void after(String call) {
            Runnable hook = action;
            if (hook != null && afterCalls.contains(call)) {
                hook.run();
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PauseHookConfig {

        @Bean
        static BeanPostProcessor memberPauseHookPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof MemberRepository) {
                        return wrap(bean);
                    }
                    return bean;
                }
            };
        }

        private static Object wrap(Object target) {
            return Proxy.newProxyInstance(
                    target.getClass().getClassLoader(), target.getClass().getInterfaces(), (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(target, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        PauseHooks.after("MemberRepository." + method.getName());
                        return result;
                    });
        }
    }
}
