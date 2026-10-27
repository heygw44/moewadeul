package com.moewadeul.basecamp.application.membership;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.application.lifecycle.BasecampTransitionService;
import com.moewadeul.basecamp.domain.BasecampErrorCode;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.ErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
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
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BasecampApprovalConcurrencyIntegrationTest {

    private static final String AT_DEFAULT_INSTANT = "2026-10-05 03:00:00";

    @Autowired
    private BasecampApprovalService basecampApprovalService;

    @Autowired
    private BasecampTransitionService basecampTransitionService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-13][BC-08] 남은 자리 1개에 서로 다른 신청 2건을 동시에 승인하면 1건만 성공하고, ACTIVE 멤버는 정원과 같으며 자동 마감된다")
    void onlyOneOfTwoConcurrentApprovalsSucceedsForLastSeat() throws Exception {
        // given
        Scenario scenario = saveScenario(3, 2);

        // when
        List<Throwable> failures = runConcurrently(approvals(scenario));

        // then
        assertThat(failures).hasSize(1).allSatisfy(BasecampApprovalConcurrencyIntegrationTest::assertLoserOfLastSeat);
        assertThat(activeMemberCount(scenario.basecampId())).isEqualTo(3);
        assertClosedBy(scenario.basecampId(), "AUTO_FULL");
        assertApprovalsMatchMembers(scenario);
        assertThat(eventCount("BASECAMP_APPROVED")).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-13][BC-08] 남은 자리 2개에 신청 5건을 동시에 승인하면 2건만 성공하고 ACTIVE 멤버는 정원을 넘지 않는다")
    void approvalsNeverExceedCapacityUnderManyConcurrentApprovals() throws Exception {
        // given
        Scenario scenario = saveScenario(4, 5);

        // when
        List<Throwable> failures = runConcurrently(approvals(scenario));

        // then
        assertThat(failures).hasSize(3).allSatisfy(BasecampApprovalConcurrencyIntegrationTest::assertLoserOfLastSeat);
        assertThat(activeMemberCount(scenario.basecampId())).isEqualTo(4);
        assertClosedBy(scenario.basecampId(), "AUTO_FULL");
        assertApprovalsMatchMembers(scenario);
    }

    @RepeatedTest(5)
    @DisplayName(
            "[F-13][BC-08] 캠프 리더의 마감과 승인이 동시에 오면 정원을 넘지 않고, 신청 상태와 멤버 행이 일치하며, 마감이 먼저면 승인은 BASECAMP_INVALID_STATE이고 신청은 대기로 남는다")
    void closeAndApprovalAreSerialized() throws Exception {
        // given
        Scenario scenario = saveScenario(4, 1);
        long applicationId = scenario.applicationIds().get(0);
        Callable<Object> close = () -> basecampTransitionService.close(scenario.basecampId(), scenario.leaderId());
        Callable<Object> approve =
                () -> basecampApprovalService.approve(scenario.basecampId(), applicationId, scenario.leaderId());

        // when
        List<Throwable> failures = runConcurrently(List.of(close, approve));

        // then
        assertThat(activeMemberCount(scenario.basecampId())).isLessThanOrEqualTo(4);
        assertClosedBy(scenario.basecampId(), "LEADER");
        assertApprovalsMatchMembers(scenario);
        if (failures.isEmpty()) {
            assertThat(applicationStatus(applicationId)).isEqualTo("APPROVED");
            return;
        }
        assertThat(failures)
                .hasSize(1)
                .allSatisfy(failure -> assertErrorCode(failure, BasecampErrorCode.BASECAMP_INVALID_STATE));
        assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
        assertThat(activeMemberCount(scenario.basecampId())).isEqualTo(2);
        assertThat(eventCount("BASECAMP_APPROVED")).isZero();
    }

    @RepeatedTest(10)
    @DisplayName(
            "[F-13] 날짜가 겹치는 두 베이스캠프의 캠프 리더가 같은 신청자를 동시에 승인하면 1건만 성공하고 나머지는 BASECAMP_DATE_CONFLICT이며 ACTIVE 멤버 행은 1개다")
    void onlyOneOfTwoOverlappingApprovalsOfSameApplicantSucceeds() throws Exception {
        // given
        long applicantId = saveVerifiedMember();
        long firstLeader = saveMember();
        long firstBasecamp = saveBasecamp(firstLeader, 4);
        insertMember(firstBasecamp, firstLeader, "LEADER");
        long firstApplication = insertApplication(firstBasecamp, applicantId, "PENDING");
        long secondLeader = saveMember();
        long secondBasecamp = saveBasecamp(secondLeader, 4);
        insertMember(secondBasecamp, secondLeader, "LEADER");
        long secondApplication = insertApplication(secondBasecamp, applicantId, "PENDING");

        // when
        List<Throwable> failures = runConcurrently(List.of(
                () -> basecampApprovalService.approve(firstBasecamp, firstApplication, firstLeader),
                () -> basecampApprovalService.approve(secondBasecamp, secondApplication, secondLeader)));

        // then
        assertThat(failures)
                .hasSize(1)
                .allSatisfy(failure -> assertErrorCode(failure, BasecampErrorCode.BASECAMP_DATE_CONFLICT));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM basecamp_member WHERE member_id = ? AND status = 'ACTIVE'",
                        Integer.class,
                        applicantId))
                .isEqualTo(1);
    }

    private List<Callable<Object>> approvals(Scenario scenario) {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (long applicationId : scenario.applicationIds()) {
            tasks.add(() -> basecampApprovalService.approve(scenario.basecampId(), applicationId, scenario.leaderId()));
        }
        return tasks;
    }

    private static void assertLoserOfLastSeat(Throwable failure) {
        assertThat(failure).isInstanceOf(BusinessException.class);
        ErrorCode code = ((BusinessException) failure).getErrorCode();
        assertThat(code).isIn(BasecampErrorCode.BASECAMP_INVALID_STATE, BasecampErrorCode.BASECAMP_FULL);
    }

    private static void assertErrorCode(Throwable failure, BasecampErrorCode expected) {
        assertThat(failure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private void assertClosedBy(long basecampId, String closedReason) {
        assertThat(jdbc.queryForMap("SELECT status, closed_reason FROM basecamp WHERE id = ?", basecampId))
                .containsEntry("status", "CLOSED")
                .containsEntry("closed_reason", closedReason);
    }

    private void assertApprovalsMatchMembers(Scenario scenario) {
        long basecampId = scenario.basecampId();
        Integer approved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp_application WHERE basecamp_id = ? AND status = 'APPROVED'",
                Integer.class,
                basecampId);
        Integer nonLeaderMembers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp_member WHERE basecamp_id = ? AND role = 'MEMBER' AND status = 'ACTIVE'",
                Integer.class,
                basecampId);
        Integer approvedWithoutMember = jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp_application a WHERE a.basecamp_id = ? AND a.status = 'APPROVED'"
                        + " AND NOT EXISTS (SELECT 1 FROM basecamp_member m WHERE m.basecamp_id = a.basecamp_id"
                        + " AND m.member_id = a.applicant_id AND m.status = 'ACTIVE')",
                Integer.class,
                basecampId);
        assertThat(approved).isEqualTo(nonLeaderMembers);
        assertThat(approvedWithoutMember).isZero();
    }

    private int activeMemberCount(long basecampId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp_member WHERE basecamp_id = ? AND status = 'ACTIVE'",
                Integer.class,
                basecampId);
    }

    private String applicationStatus(long applicationId) {
        return jdbc.queryForObject("SELECT status FROM basecamp_application WHERE id = ?", String.class, applicationId);
    }

    private int eventCount(String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_type = ?", Integer.class, eventType);
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private long saveVerifiedMember() {
        long memberId = saveMember();
        jdbc.update(
                "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                        + " created_at, updated_at) VALUES (?, 2007, 'MALE', ?, 'FAKE', ?, ?, ?)",
                memberId,
                "%064d".formatted(memberId),
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return memberId;
    }

    private Scenario saveScenario(int capacity, int pendingCount) {
        long leaderId = saveMember();
        long basecampId = saveBasecamp(leaderId, capacity);
        insertMember(basecampId, leaderId, "LEADER");
        long existingMemberId = saveMember();
        insertMember(basecampId, existingMemberId, "MEMBER");
        insertApplication(basecampId, existingMemberId, "APPROVED");
        List<Long> applicationIds = new ArrayList<>();
        for (int i = 0; i < pendingCount; i++) {
            applicationIds.add(insertApplication(basecampId, saveVerifiedMember(), "PENDING"));
        }
        return new Scenario(basecampId, leaderId, applicationIds);
    }

    private long saveBasecamp(long leaderId, int capacity) {
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        long spotId = jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
        LocalDate startDate = LocalDate.of(2026, 10, 20);
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '굴업도 주말 1박', '함께 가요', ?, ?, ?, 'RECRUITING', ?, ?)",
                leaderId,
                spotId,
                startDate,
                startDate.plusDays(2),
                capacity,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
    }

    private void insertMember(long basecampId, long memberId, String role) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)",
                basecampId,
                memberId,
                role,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
    }

    private long insertApplication(long basecampId, long applicantId, String status) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, applied_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                basecampId,
                applicantId,
                status,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return jdbc.queryForObject("SELECT MAX(id) FROM basecamp_application", Long.class);
    }

    private List<Throwable> runConcurrently(List<Callable<Object>> actions) throws Exception {
        int threads = actions.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) {
                Callable<Object> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                };
                futures.add(executor.submit(task));
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

    private record Scenario(long basecampId, long leaderId, List<Long> applicationIds) {}
}
