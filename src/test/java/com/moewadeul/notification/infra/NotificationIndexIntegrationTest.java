package com.moewadeul.notification.infra;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class NotificationIndexIntegrationTest {

    // countByMemberIdAndReadAtIsNull이 만드는 SQL과 같은 조건이다.
    private static final String COUNT_UNREAD_SQL =
            "SELECT COUNT(n.id) FROM notification n WHERE n.member_id = ? AND n.read_at IS NULL";

    private static final int READ_NOTIFICATIONS = 300;
    private static final int UNREAD_NOTIFICATIONS = 5;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-20] 안 읽은 알림 수를 셀 때 MySQL은 (member_id, read_at) 인덱스만 읽고 알림 행을 다시 읽지 않는다")
    void unreadCountReadsOnlyMemberReadIndex() {
        // given: 읽은 알림이 많고 안 읽은 알림이 적은 회원이다.
        // 인덱스에 read_at이 없으면 MySQL이 이 회원의 알림을 모두 찾은 뒤 행마다 read_at을 확인한다.
        long memberId = memberRepository.saveAndFlush(aMember().build()).getId();
        insertNotifications(memberId, READ_NOTIFICATIONS, true);
        insertNotifications(memberId, UNREAD_NOTIFICATIONS, false);
        jdbc.execute("ANALYZE TABLE notification");

        // when
        Map<String, Object> plan = jdbc.queryForMap("EXPLAIN " + COUNT_UNREAD_SQL, memberId);
        Long unread = jdbc.queryForObject(COUNT_UNREAD_SQL, Long.class, memberId);

        // then
        assertThat(plan.get("key")).isEqualTo("idx_notification_member_read");
        assertThat((String) plan.get("Extra")).contains("Using index");
        assertThat(unread).isEqualTo(UNREAD_NOTIFICATIONS);
    }

    private void insertNotifications(long memberId, int count, boolean read) {
        String readAt = read ? "UTC_TIMESTAMP(6)" : "NULL";
        for (int i = 0; i < count; i++) {
            jdbc.update(
                    "INSERT INTO notification (member_id, type, title, body, link, dedup_key, read_at, email_status,"
                            + " created_at) VALUES (?, 'BASECAMP_APPROVED', '합류 승인', '승인됐습니다.', '/basecamps/1',"
                            + " ?, " + readAt + ", 'SENT', UTC_TIMESTAMP(6))",
                    memberId,
                    "index-" + read + "-" + i);
        }
    }
}
