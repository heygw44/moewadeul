package com.moewadeul.basecamp.application.lifecycle;

import static com.moewadeul.basecamp.application.lifecycle.BasecampAutoTransitionFixture.END_DATE;
import static com.moewadeul.basecamp.application.lifecycle.BasecampAutoTransitionFixture.START_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.basecamp.infra.lifecycle.BasecampAutoTransitionMapper;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class BasecampAutoTransitionRuleIntegrationTest {

    private static final Instant NOW =
            END_DATE.plusDays(1).atStartOfDay(KoreaTime.ZONE).toInstant().plusNanos(123_456_000);
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, KoreaTime.ZONE);

    private static final String BASECAMP_COLUMNS =
            "status, closed_reason, confirmed_at, canceled_at, cancel_reason," + " completed_at, updated_at";

    private static final LocalDateTime NOW_IN_DB = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Autowired
    private BasecampAutoTransitionMapper mapper;

    @Autowired
    private BasecampAutoTransitionApplier applier;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private BasecampAutoTransitionFixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = new BasecampAutoTransitionFixture(jdbc, memberRepository, jsonMapper);
    }

    @FunctionalInterface
    private interface DueSelection {
        List<Long> select(BasecampAutoTransitionMapper mapper);
    }

    @FunctionalInterface
    private interface AutoTransition {
        boolean apply(BasecampAutoTransitionApplier applier, long id);
    }

    private record Rule(
            String name,
            int extraMembers,
            Set<BasecampStatus> allowedFrom,
            AutoTransition apply,
            DueSelection selection,
            Map<String, Object> changedColumns) {

        @Override
        public String toString() {
            return name;
        }
    }

    private static final Rule CONFIRM_RULE = new Rule(
            "확정",
            1,
            Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED),
            (applier, id) -> applier.processDayBeforeDeparture(id, NOW),
            mapper -> mapper.selectDueForDepartureIds(START_DATE, 0, 100),
            Map.of("status", "CONFIRMED", "confirmed_at", NOW_IN_DB, "updated_at", NOW_IN_DB));

    private static final Rule CANCEL_RULE = new Rule(
            "인원 부족 취소",
            0,
            Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED),
            (applier, id) -> applier.processDayBeforeDeparture(id, NOW),
            mapper -> mapper.selectDueForDepartureIds(START_DATE, 0, 100),
            Map.of(
                    "status",
                    "CANCELED",
                    "canceled_at",
                    NOW_IN_DB,
                    "cancel_reason",
                    "NOT_ENOUGH_MEMBERS",
                    "updated_at",
                    NOW_IN_DB));

    private static final Rule COMPLETE_RULE = new Rule(
            "완료",
            1,
            Set.of(BasecampStatus.CONFIRMED),
            (applier, id) -> applier.complete(id, TODAY, NOW),
            mapper -> mapper.selectDueForCompletionIds(TODAY, 0, 100),
            Map.of("status", "COMPLETED", "completed_at", NOW_IN_DB, "updated_at", NOW_IN_DB));

    static Stream<Arguments> everyRuleFromEveryStatus() {
        return Stream.of(CONFIRM_RULE, CANCEL_RULE, COMPLETE_RULE)
                .flatMap(rule -> Stream.of(BasecampStatus.values()).map(status -> Arguments.of(rule, status)));
    }

    @ParameterizedTest(name = "{0} / 시작 상태 {1}")
    @MethodSource("everyRuleFromEveryStatus")
    @DisplayName("자동 전이는 허용된 시작 상태에서만 일어나고, 그때 시각 컬럼과 대기 신청만 바뀌며 마감 사유는 지워진다")
    void autoTransitionRuleFromEveryStatus(Rule rule, BasecampStatus status) {
        // given
        long id = saveWithApplications(status, rule.extraMembers());
        Map<String, Object> rowBefore = rowOf(id);
        List<Map<String, Object>> applicationsBefore = applicationRowsOf(id);

        // when
        boolean selectedByScheduler = rule.selection().select(mapper).contains(id);
        boolean transitioned = transactionTemplate.execute(tx -> rule.apply().apply(applier, id));

        // then
        boolean allowed = rule.allowedFrom().contains(status);
        assertThat(transitioned).isEqualTo(allowed);
        assertThat(selectedByScheduler).isEqualTo(allowed);
        if (!allowed) {
            assertThat(rowOf(id)).isEqualTo(rowBefore);
            assertThat(applicationRowsOf(id)).isEqualTo(applicationsBefore);
            return;
        }
        Map<String, Object> expectedRow = new HashMap<>(rowBefore);
        expectedRow.put("closed_reason", null);
        expectedRow.putAll(rule.changedColumns());
        assertThat(rowOf(id)).isEqualTo(expectedRow);
        assertThat(applicationRowsOf(id))
                .containsExactly(Map.of("status", "EXPIRED", "updated_at", NOW_IN_DB), applicationsBefore.get(1));
    }

    @ParameterizedTest(name = "종료일 {0}")
    @MethodSource("endDateOffsetsFromToday")
    @DisplayName("완료 처리는 종료일이 오늘보다 이전일 때만 확정된 베이스캠프를 바꾸고, 완료 대상 조회도 같은 기준을 쓴다")
    void completionGuardOnEndDate(long daysBeforeToday, int expectedChanged) {
        // given
        LocalDate endDate = TODAY.minusDays(daysBeforeToday);
        long id = fixture.saveBasecamp("CONFIRMED", null, endDate.minusDays(2), endDate);

        // when
        boolean selected = mapper.selectDueForCompletionIds(TODAY, 0, 100).contains(id);
        boolean changed = applier.complete(id, TODAY, NOW);

        // then
        assertThat(changed).isEqualTo(expectedChanged == 1);
        assertThat(selected).isEqualTo(expectedChanged == 1);
    }

    static Stream<Arguments> endDateOffsetsFromToday() {
        return Stream.of(Arguments.of(1L, 1), Arguments.of(0L, 0), Arguments.of(-1L, 0));
    }

    private long saveWithApplications(BasecampStatus status, int extraMembers) {
        String closedReason = status == BasecampStatus.CLOSED ? "LEADER" : null;
        long id = fixture.saveBasecamp(status.name(), closedReason, START_DATE, END_DATE);
        for (int i = 0; i < extraMembers; i++) {
            fixture.insertMember(id, fixture.saveVerifiedMember(), "MEMBER", "ACTIVE");
        }
        fixture.insertPendingApplication(id);
        long approved = fixture.insertPendingApplication(id);
        jdbc.update("UPDATE basecamp_application SET status = 'APPROVED' WHERE id = ?", approved);
        return id;
    }

    private Map<String, Object> rowOf(long id) {
        return jdbc.queryForMap("SELECT " + BASECAMP_COLUMNS + " FROM basecamp WHERE id = ?", id);
    }

    private List<Map<String, Object>> applicationRowsOf(long basecampId) {
        return jdbc.queryForList(
                "SELECT status, updated_at FROM basecamp_application WHERE basecamp_id = ? ORDER BY id", basecampId);
    }
}
