package com.moewadeul.memory.application.report;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.application.MemoryCommandService;
import com.moewadeul.memory.application.MemoryWriteCommand;
import com.moewadeul.memory.application.moderation.MemoryModerationService;
import com.moewadeul.memory.domain.MemoryErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemoryReportConcurrencyIntegrationTest {

    private static final long MISSING_MEMBER_ID = 999_999_999L;

    private static final int THREADS = 10;
    private static final int PENDING_REVIEW_REPORTS = 5;
    private static final MemoryReportCommand REPORT = new MemoryReportCommand("SPAM", "광고 사진입니다");

    @Autowired
    private MemoryReportService memoryReportService;

    @Autowired
    private MemoryModerationService memoryModerationService;

    @Autowired
    private MemoryCommandService memoryCommandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-33][MM-10] 신고 3건인 기록에 서로 다른 회원 2명이 동시에 신고하면 둘 다 저장하고 기록이 PENDING_REVIEW가 된다")
    void concurrentReportsCrossingThresholdMoveToPendingReview() throws Exception {
        // given
        long memoryId = savePublicMemory(saveMember());
        for (int i = 0; i < PENDING_REVIEW_REPORTS - 2; i++) {
            memoryReportService.report(saveMember(), memoryId, REPORT);
        }
        List<Long> memberIds = saveMembers(2);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            memoryReportService.report(memberId, memoryId, REPORT);
            return null;
        });

        // then
        assertThat(failures).isEmpty();
        assertThat(reportCount(memoryId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(memoryStatus(memoryId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName(
            "[F-33][MM-10] 신고가 없는 기록에 서로 다른 회원 10명이 동시에 신고하면 5건째에 PENDING_REVIEW가 되고, 그 뒤 신고는 NOT_FOUND이며 신고 행은 5건이다")
    void concurrentReportsStopAtThreshold() throws Exception {
        // given
        long memoryId = savePublicMemory(saveMember());
        List<Long> memberIds = saveMembers(THREADS);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            memoryReportService.report(memberId, memoryId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - PENDING_REVIEW_REPORTS)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode().name()).isEqualTo("NOT_FOUND")));
        assertThat(reportCount(memoryId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(memoryStatus(memoryId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName("[F-33][MM-10] 같은 회원이 같은 기록을 동시에 신고하면 1건만 성공하고 나머지는 MEMORY_ALREADY_REPORTED다")
    void sameMemberReportsOnlyOnce() throws Exception {
        // given
        long memoryId = savePublicMemory(saveMember());
        long memberId = saveMember();

        // when
        List<Throwable> failures = runConcurrently(Collections.nCopies(THREADS, memberId), id -> {
            memoryReportService.report(id, memoryId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(MemoryErrorCode.MEMORY_ALREADY_REPORTED)));
        assertThat(reportCount(memoryId)).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-33][MM-10][MM-11] 관리자 숨김과 5번째 신고가 동시에 오면 기록은 항상 HIDDEN이고, 신고가 숨김보다 늦으면 NOT_FOUND로만 거부된다")
    void hideRacingFifthReportEndsHidden() throws Exception {
        // given
        long memoryId = savePublicMemory(saveMember());
        for (int i = 0; i < PENDING_REVIEW_REPORTS - 1; i++) {
            memoryReportService.report(saveMember(), memoryId, REPORT);
        }
        long admin = saveMember();
        long fifthReporter = saveMember();

        // when
        List<Throwable> failures = runConcurrently(List.of(() -> memoryModerationService.hide(memoryId, admin), () -> {
            memoryReportService.report(fifthReporter, memoryId, REPORT);
            return null;
        }));

        // then
        assertThat(memoryStatus(memoryId)).isEqualTo("HIDDEN");
        assertThat(auditCount("MEMORY_HIDE")).isEqualTo(1);
        if (failures.isEmpty()) {
            assertThat(reportCount(memoryId)).isEqualTo(PENDING_REVIEW_REPORTS);
        } else {
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode().name()).isEqualTo("NOT_FOUND"));
            assertThat(reportCount(memoryId)).isEqualTo(PENDING_REVIEW_REPORTS - 1);
        }
    }

    private long savePublicMemory(long authorId) {
        return memoryCommandService.write(
                authorId,
                new MemoryWriteCommand(null, 37.25, 127.25, LocalDate.of(2026, 10, 4), 1, null, "PUBLIC", List.of()));
    }

    @Test
    @DisplayName("[F-33] 신고자가 없어서 외래 키가 깨지면 중복 신고로 바꾸지 않고 무결성 위반을 그대로 던진다")
    void foreignKeyViolationIsNotReportedAsDuplicate() {
        // given
        long memoryId = savePublicMemory(saveMember());

        // when, then
        assertThatThrownBy(() -> memoryReportService.report(MISSING_MEMBER_ID, memoryId, REPORT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private List<Long> saveMembers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(saveMember());
        }
        return ids;
    }

    private int reportCount(long memoryId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM memory_report WHERE memory_id = ?", Integer.class, memoryId);
    }

    private String memoryStatus(long memoryId) {
        return jdbc.queryForObject("SELECT status FROM memory WHERE id = ?", String.class, memoryId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE action = ?", Integer.class, action);
    }

    private <T> List<Throwable> runConcurrently(List<Long> memberIds, MemberAction<T> action) throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (long memberId : memberIds) {
            tasks.add(() -> action.run(memberId));
        }
        return runConcurrently(tasks);
    }

    private List<Throwable> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        int threads = tasks.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
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

    private List<Throwable> collectFailures(List<Future<Object>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }

    @FunctionalInterface
    private interface MemberAction<T> {
        T run(long memberId) throws Exception;
    }
}
