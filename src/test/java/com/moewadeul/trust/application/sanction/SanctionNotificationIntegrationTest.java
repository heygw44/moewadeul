package com.moewadeul.trust.application.sanction;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.outbox.application.OutboxPublisher;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SanctionNotificationIntegrationTest {

    @Autowired
    private MemberReportActionService memberReportActionService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private SanctionFixture sanctionFixture;
    private long adminId;
    private long targetId;

    @BeforeEach
    void setUp() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        sanctionFixture = new SanctionFixture(fixture, new MemberReportFixture(jdbc), memberReportActionService, clock);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[F-20][SN-10] 경고를 확정하고 이벤트를 발행하면 제재받은 회원의 알림함에 경고 알림이 생긴다")
    void warningNotifiesSanctionedMember() {
        // when
        confirm(SanctionType.WARNING);
        outboxPublisher.publishPending();

        // then
        List<Map<String, Object>> notifications = jdbc.queryForList(
                "SELECT member_id, type, title, body, link FROM notification WHERE type = 'SANCTION_CONFIRMED'");
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0))
                .containsEntry("member_id", targetId)
                .containsEntry("type", "SANCTION_CONFIRMED")
                .containsEntry("title", "이용 제재 안내")
                .containsEntry("body", "경고를 받았어요.")
                .containsEntry("link", null);
    }

    @Test
    @DisplayName("[F-20][SN-10] 7일 정지를 확정하면 알림 본문에 한국 시간 종료 시각이 들어간다")
    void suspensionNotificationShowsEndInKoreanTime() {
        // given
        confirm(SanctionType.WARNING);
        confirm(SanctionType.SUSPEND_7D);

        // when
        outboxPublisher.publishPending();

        // then
        List<String> bodies = jdbc.queryForList(
                "SELECT body FROM notification WHERE type = 'SANCTION_CONFIRMED' ORDER BY id", String.class);
        assertThat(bodies).containsExactly("경고를 받았어요.", "2026-10-12 12:00까지 이용이 정지돼요.");
    }

    private void confirm(SanctionType type) {
        sanctionFixture.confirm(targetId, adminId, type, "욕설");
    }
}
