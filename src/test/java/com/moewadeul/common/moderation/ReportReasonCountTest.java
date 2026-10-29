package com.moewadeul.common.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportReasonCountTest {

    @Test
    @DisplayName("[CM-08][MM-11] 대상마다 모든 사유가 enum 선언 순서로 들어가고, 신고가 없는 사유와 대상은 0이다")
    void fillsEveryReasonInDeclarationOrder() {
        // given
        List<ReportReasonCount> counts =
                List.of(new ReportReasonCount(1L, "MONEY_SCAM", 2), new ReportReasonCount(1L, "SPAM", 3));

        // when
        Map<Long, Map<String, Long>> byTarget =
                ReportReasonCount.byTarget(ContentReportReason.class, List.of(1L, 2L), counts);

        // then
        assertThat(byTarget.get(1L))
                .containsExactly(
                        Map.entry("SPAM", 3L),
                        Map.entry("ABUSE", 0L),
                        Map.entry("ILLEGAL_CAMPING", 0L),
                        Map.entry("PRIVACY", 0L),
                        Map.entry("MONEY_SCAM", 2L),
                        Map.entry("OTHER", 0L));
        assertThat(byTarget.get(2L))
                .containsOnlyKeys("SPAM", "ABUSE", "ILLEGAL_CAMPING", "PRIVACY", "MONEY_SCAM", "OTHER");
        assertThat(byTarget.get(2L).values()).containsOnly(0L);
    }
}
