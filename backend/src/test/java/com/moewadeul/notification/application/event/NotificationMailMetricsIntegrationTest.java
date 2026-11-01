package com.moewadeul.notification.application.event;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class NotificationMailMetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[NFR-09] 보낼 메일이 없으면 보낼 메일 수, 실패 수, 가장 오래된 보낼 메일의 나이가 모두 0이다")
    void reportsZeroWhenNothingIsPending() {
        assertThat(mails("pending")).isZero();
        assertThat(mails("failed")).isZero();
        assertThat(oldestPendingAgeSeconds()).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 메일 상태별 알림 수와 가장 오래된 보낼 메일의 알림이 생긴 뒤 지난 시간을 지표로 읽고, 보냈거나 건너뛴 메일은 세지 않는다")
    void reportsBacklogFromDatabase() {
        // given
        Member member = memberRepository.saveAndFlush(aMember().build());
        insert(member.getId(), "1:1", "PENDING");
        insert(member.getId(), "2:1", "PENDING");
        insert(member.getId(), "3:1", "FAILED");
        insert(member.getId(), "4:1", "SENT");
        insert(member.getId(), "5:1", "SKIPPED");

        // when
        clock.advance(Duration.ofMinutes(15));

        // then
        assertThat(mails("pending")).isEqualTo(2);
        assertThat(mails("failed")).isEqualTo(1);
        assertThat(oldestPendingAgeSeconds()).isEqualTo(900);
    }

    private void insert(long memberId, String dedupKey, String emailStatus) {
        jdbcTemplate.update(
                "INSERT INTO notification (member_id, type, title, body, dedup_key, email_status, created_at) "
                        + "VALUES (?, 'BASECAMP_APPROVED', '합류 승인', '승인됐어요.', ?, ?, ?)",
                memberId,
                dedupKey,
                emailStatus,
                Timestamp.from(clock.instant()));
    }

    private double mails(String status) {
        return meterRegistry
                .get("moewadeul.notification.mails")
                .tag("status", status)
                .gauge()
                .value();
    }

    private double oldestPendingAgeSeconds() {
        return meterRegistry
                .get("moewadeul.notification.mail.oldest.pending.age")
                .gauge()
                .value();
    }
}
