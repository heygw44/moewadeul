package com.moewadeul.trust.infra.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.domain.report.ReportType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class AdminMemberReportPrioritySqlParityIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private AdminMemberReportMapper mapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;
    private MemberReportFixture reportFixture;
    private long basecampId;
    private long targetId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        reportFixture = new MemberReportFixture(jdbc);
        basecampId = fixture.saveBasecamp("COMPLETED", BASE);
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[F-21][SF-02][SN-05] 신고 목록 정렬은 모든 신고 유형에서 ReportType.priority 내림차순, 접수 시각 오름차순, ID 오름차순과 같다")
    void listOrderAgreesWithReportTypePriority() {
        // given
        Map<Long, ReportCase> cases = insertEveryTypeWithMixedCreatedAt();
        List<Long> expected = cases.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Long, ReportCase> entry) ->
                                entry.getValue().type().priority())
                        .reversed()
                        .thenComparing(entry -> entry.getValue().createdAt())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();

        // when
        List<Long> listed = mapper.selectList(null, null, 0, cases.size() + 1).stream()
                .map(AdminMemberReportRow::reportId)
                .toList();

        // then
        assertThat(listed).isEqualTo(expected);
        assertThat(expected.stream().map(id -> cases.get(id).type().priority()).distinct())
                .containsExactly(2, 1, 0);
    }

    private Map<Long, ReportCase> insertEveryTypeWithMixedCreatedAt() {
        List<ReportCase> cases = new ArrayList<>();
        for (Duration createdOffset :
                List.of(Duration.ofHours(3), Duration.ZERO, Duration.ofHours(3), Duration.ofHours(1))) {
            for (ReportType type : ReportType.values()) {
                cases.add(new ReportCase(type, BASE.plus(createdOffset)));
            }
        }
        return cases.stream().collect(Collectors.toMap(this::insert, reportCase -> reportCase));
    }

    private long insert(ReportCase reportCase) {
        long reporterId = fixture.saveVerifiedMember(TestSequence.nickname());
        if (reportCase.type() == ReportType.INAPPROPRIATE_REVIEW) {
            long reviewId = fixture.insertReview(basecampId, targetId, reporterId, true, BASE);
            return reportFixture.insertReviewReport(
                    reporterId, targetId, basecampId, reviewId, "RECEIVED", reportCase.createdAt());
        }
        return reportFixture.insertMemberReport(
                reporterId, targetId, basecampId, reportCase.type().name(), "RECEIVED", reportCase.createdAt());
    }

    private record ReportCase(ReportType type, Instant createdAt) {}
}
