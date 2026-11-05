package com.moewadeul.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.application.lifecycle.BasecampSanctionCleanupService;
import com.moewadeul.basecamp.application.membership.BasecampApprovalService;
import com.moewadeul.basecamp.domain.RemovalCause;
import com.moewadeul.common.patch.PatchField;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.application.profile.MyInfoService;
import com.moewadeul.member.application.profile.MyInfoUpdateCommand;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.application.enrollment.ProgramApplyService;
import com.moewadeul.program.application.enrollment.ProgramPaymentService;
import com.moewadeul.program.application.enrollment.ProgramSanctionCleanupService;
import com.moewadeul.program.domain.ProgramCancelReason;
import com.moewadeul.trust.application.report.MemberReportActionCommand;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemberLockOrderConcurrencyIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String AT = "2026-10-05 03:00:00";

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private BasecampApprovalService approvalService;

    @Autowired
    private BasecampSanctionCleanupService basecampCleanupService;

    @Autowired
    private ProgramSanctionCleanupService programCleanupService;

    @Autowired
    private ProgramApplyService applyService;

    @Autowired
    private ProgramPaymentService paymentService;

    @Autowired
    private MemberReportActionService reportActionService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private MyInfoService myInfoService;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(10)
    @DisplayName("[SN-04] 한 회원을 두고 승인, 제재, 탈퇴, 행사 신청과 취소, 내 정보 수정, 정리 작업이 동시에 돌아도 잠금 실패가 나지 않는다")
    void everyPathTouchingOneMemberAvoidsLockFailures() throws Exception {
        long target = saveVerifiedMember();
        long admin = memberFixture.save().getId();
        long reporter = memberFixture.save().getId();
        ProgramApplyFixture programs = new ProgramApplyFixture(jdbc, memberRepository);
        long appliedProgram = programs.saveProgram(5, false);
        long pendingApplication = programs.saveApplication(appliedProgram, target, "PENDING_PAYMENT");
        long openProgram = programs.saveProgram(5, false);
        List<Callable<Object>> actions = new ArrayList<>();
        long firstBasecamp = 0;
        for (int i = 0; i < 3; i++) {
            long leader = memberFixture.save().getId();
            long basecamp = saveBasecamp(leader);
            firstBasecamp = firstBasecamp == 0 ? basecamp : firstBasecamp;
            long application = saveApplication(basecamp, target);
            actions.add(() -> approvalService.approve(basecamp, application, leader));
        }
        long report = saveReport(reporter, target, firstBasecamp);
        actions.add(() ->
                reportActionService.act(new MemberReportActionCommand(report, admin, "SUSPEND_7D", "사유", false, "메모")));
        actions.add(() -> {
            withdrawalService.withdraw(target, PASSWORD, "203.0.113.9");
            return null;
        });
        actions.add(() -> applyService.apply(target, openProgram));
        actions.add(() -> paymentService.cancel(target, pendingApplication));
        actions.add(() -> myInfoService.update(
                target,
                new MyInfoUpdateCommand(
                        PatchField.of(TestSequence.nickname()), PatchField.absent(), PatchField.absent())));
        actions.add(() -> {
            basecampCleanupService.cleanUp(target, RemovalCause.SANCTION);
            return null;
        });
        actions.add(() -> {
            programCleanupService.cleanUp(target, ProgramCancelReason.SANCTIONED);
            return null;
        });

        List<Throwable> failures = runConcurrently(actions);

        assertThat(failures).noneMatch(failure -> failure instanceof PessimisticLockingFailureException);
    }

    private long saveVerifiedMember() {
        long memberId = memberFixture.saveWithPassword(PASSWORD).getId();
        jdbc.update(
                "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                        + " created_at, updated_at) VALUES (?, 1990, 'MALE', ?, 'FAKE', ?, ?, ?)",
                memberId,
                "%064d".formatted(memberId),
                AT,
                AT,
                AT);
        return memberId;
    }

    private long saveBasecamp(long leader) {
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        long spot = jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
        LocalDate start = LocalDate.of(2026, 10, 20);
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '주말 1박', '함께 가요', ?, ?, 3, 'RECRUITING', ?, ?)",
                leader,
                spot,
                start,
                start.plusDays(2),
                AT,
                AT);
        long basecamp = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, 'LEADER', 'ACTIVE', ?, ?, ?)",
                basecamp,
                leader,
                AT,
                AT,
                AT);
        return basecamp;
    }

    private long saveApplication(long basecamp, long applicant) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, applied_at, created_at,"
                        + " updated_at) VALUES (?, ?, 'PENDING', ?, ?, ?)",
                basecamp,
                applicant,
                AT,
                AT,
                AT);
        return jdbc.queryForObject("SELECT MAX(id) FROM basecamp_application", Long.class);
    }

    private long saveReport(long reporter, long target, long basecamp) {
        jdbc.update(
                "INSERT INTO member_report (reporter_id, target_member_id, basecamp_id, kind, type, content, urgent,"
                        + " status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'MEMBER', 'NO_SHOW', '내용', 0, 'IN_REVIEW', ?, ?)",
                reporter,
                target,
                basecamp,
                AT,
                AT);
        return jdbc.queryForObject("SELECT MAX(id) FROM member_report", Long.class);
    }

    private List<Throwable> runConcurrently(List<Callable<Object>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
