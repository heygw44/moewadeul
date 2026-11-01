package com.moewadeul.aisearch.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class AiSearchUsageCleanupIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private AiSearchUsageCleanupService cleanupService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-27][PV-11] 오늘(한국 날짜)에서 90일을 뺀 날짜보다 앞선 행만 지운다. 90일 전 행은 남긴다")
    void deletesOnlyRowsOlderThanRetention() {
        // given
        long memberId = memberRepository.saveAndFlush(aMember().build()).getId();
        insertUsage(memberId, TODAY.minusDays(89));
        insertUsage(memberId, TODAY.minusDays(90));
        insertUsage(memberId, TODAY.minusDays(91));

        // when
        int deleted = cleanupService.deleteExpiredUsage();

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(remainingDates()).containsExactly(TODAY.minusDays(90), TODAY.minusDays(89));
    }

    @Test
    @DisplayName("[F-27][PV-11] 지울 행이 한 묶음(1000건)보다 많아도 모두 지운다")
    void deletesInBatchesUntilNothingLeft() {
        // given
        long memberId = memberRepository.saveAndFlush(aMember().build()).getId();
        jdbc.update(
                "INSERT INTO ai_search_usage (member_id, usage_date, request_count, updated_at)"
                        + " SELECT ?, DATE_SUB(?, INTERVAL 100 + seq DAY), 1, NOW(6) FROM ("
                        + " SELECT a.n + b.n * 10 + c.n * 100 AS seq FROM"
                        + " (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5"
                        + " UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,"
                        + " (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5"
                        + " UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,"
                        + " (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5"
                        + " UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c) t",
                memberId,
                TODAY);
        insertUsage(memberId, TODAY);

        // when
        int deleted = cleanupService.deleteExpiredUsage();

        // then
        assertThat(deleted).isEqualTo(1000);
        assertThat(remainingDates()).containsExactly(TODAY);
    }

    private void insertUsage(long memberId, LocalDate date) {
        jdbc.update(
                "INSERT INTO ai_search_usage (member_id, usage_date, request_count, updated_at)"
                        + " VALUES (?, ?, 1, NOW(6))",
                memberId,
                date);
    }

    private List<LocalDate> remainingDates() {
        return jdbc.queryForList("SELECT usage_date FROM ai_search_usage ORDER BY usage_date", LocalDate.class);
    }
}
