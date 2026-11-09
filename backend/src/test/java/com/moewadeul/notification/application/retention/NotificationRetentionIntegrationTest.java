package com.moewadeul.notification.application.retention;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class NotificationRetentionIntegrationTest {

    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);
    private static final String INSERT_SQL =
            "INSERT INTO notification (member_id, type, title, body, link, dedup_key, read_at, email_status,"
                    + " created_at) VALUES (?, 'BASECAMP_APPROVED', '합류 승인', '승인됐습니다.', '/basecamps/1', ?, ?, ?, ?)";
    private static final String SELECT_IDS_SQL = "SELECT id FROM notification WHERE read_at < ?"
            + " AND email_status <> 'PENDING' ORDER BY read_at LIMIT 1000";

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private NotificationRetentionService retentionService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.saveAndFlush(aMember().build()).getId();
    }

    @Test
    @DisplayName("[F-20] 읽은 지 91일 된 알림은 지우고, 89일 된 알림과 안 읽은 알림은 남긴다")
    void deletesOnlyNotificationsReadMoreThanNinetyDaysAgo() {
        // given
        long readNinetyOneDaysAgo = insert(daysAgo(91), "SENT");
        long readEightyNineDaysAgo = insert(daysAgo(89), "SENT");
        long unread = insert(null, "SKIPPED");

        // when
        int deleted = retentionService.deleteExpiredRead();

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(exists(readNinetyOneDaysAgo)).isFalse();
        assertThat(exists(readEightyNineDaysAgo)).isTrue();
        assertThat(exists(unread)).isTrue();
    }

    @Test
    @DisplayName("[F-20] 오래전에 읽었어도 메일을 아직 보내지 않은 알림은 남기고, 메일이 끝난 알림은 상태와 관계없이 지운다")
    void keepsNotificationsWithPendingMail() {
        // given
        long pending = insert(daysAgo(91), "PENDING");
        long sent = insert(daysAgo(91), "SENT");
        long skipped = insert(daysAgo(91), "SKIPPED");
        long failed = insert(daysAgo(91), "FAILED");

        // when
        int deleted = retentionService.deleteExpiredRead();

        // then
        assertThat(deleted).isEqualTo(3);
        assertThat(exists(pending)).isTrue();
        assertThat(exists(sent)).isFalse();
        assertThat(exists(skipped)).isFalse();
        assertThat(exists(failed)).isFalse();
    }

    @Test
    @DisplayName("[F-20] 지울 알림이 한 쪽 크기보다 많아도 모두 지운다")
    void deletesAllExpiredBeyondBatchSize() {
        // given
        int total = NotificationRetentionService.BATCH_SIZE * 2 + 1;
        insertMany(total, daysAgo(91));
        long recent = insert(daysAgo(1), "SENT");

        // when
        int deleted = retentionService.deleteExpiredRead();

        // then
        assertThat(deleted).isEqualTo(total);
        assertThat(count()).isEqualTo(1);
        assertThat(exists(recent)).isTrue();
    }

    @Test
    @DisplayName("[F-20] 정리 대상 id는 (read_at, email_status) 인덱스만 읽어 고르고 정렬을 따로 하지 않는다")
    void selectionUsesReadAtIndex() {
        // given
        insertMany(300, daysAgo(1));
        insertMany(5, daysAgo(91));
        for (int i = 0; i < 50; i++) {
            insert(null, "SKIPPED");
        }
        jdbc.execute("ANALYZE TABLE notification");

        // when
        Map<String, Object> plan = jdbc.queryForMap("EXPLAIN " + SELECT_IDS_SQL, format(daysAgo(90)));

        // then
        assertThat(plan.get("key")).isEqualTo("idx_notification_read_at_email_status");
        assertThat(plan.get("type")).isEqualTo("range");
        assertThat((String) plan.get("Extra")).contains("Using index").doesNotContain("filesort");
    }

    private Instant daysAgo(int days) {
        return clock.instant().minus(Duration.ofDays(days));
    }

    private long insert(Instant readAt, String emailStatus) {
        jdbc.update(INSERT_SQL, memberId, nextKey(), readAt == null ? null : format(readAt), emailStatus, now());
        return jdbc.queryForObject("SELECT MAX(id) FROM notification", Long.class);
    }

    private void insertMany(int count, Instant readAt) {
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] {memberId, nextKey(), format(readAt), "SENT", now()});
        }
        jdbc.batchUpdate(INSERT_SQL, rows);
    }

    private String nextKey() {
        return "retention:" + sequence.incrementAndGet();
    }

    private String now() {
        return format(clock.instant());
    }

    private static String format(Instant instant) {
        return DATETIME_UTC.format(instant);
    }

    private boolean exists(long id) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id = ?", Integer.class, id);
        return found != null && found == 1;
    }

    private int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class);
        return count == null ? 0 : count;
    }
}
