package com.moewadeul.basecamp.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class BasecampContactCleanupIntegrationTest {

    private static final String CONTACT = "https://open.kakao.com/o/abcd";
    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    @Autowired
    private BasecampContactCleanupService cleanupService;

    @Autowired
    private MutableClock clock;

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
        clock.setInstant(NOW);
    }

    @Test
    @DisplayName("[BC-23][PV-13] 완료한 지 7일이 된 베이스캠프의 연락 수단은 지우고, 6일 된 베이스캠프의 것은 남긴다")
    void clearsContactOnlyAfterVisibleWindow() {
        long sevenDays = completedBasecampWithContact(Basecamp.CONTACT_VISIBLE_AFTER_COMPLETION.toSeconds());
        long sixDays = completedBasecampWithContact(
                Basecamp.CONTACT_VISIBLE_AFTER_COMPLETION.minusDays(1).toSeconds());

        int cleared = cleanupService.clearExpiredContacts();

        assertThat(cleared).isEqualTo(1);
        assertThat(contactOf(sevenDays)).isNull();
        assertThat(contactOf(sixDays)).isEqualTo(CONTACT);
    }

    @Test
    @DisplayName("[BC-23][PV-13] 취소된 베이스캠프의 연락 수단은 시점과 상관없이 지운다")
    void clearsContactOfCanceledBasecamp() {
        long canceled = basecampWithContact("CANCELED");

        cleanupService.clearExpiredContacts();

        assertThat(contactOf(canceled)).isNull();
    }

    @Test
    @DisplayName("[BC-23][PV-13] 확정 전·확정 상태 베이스캠프의 연락 수단은 남긴다")
    void keepsContactWhileStillNeeded() {
        long recruiting = basecampWithContact("RECRUITING");
        long confirmed = basecampWithContact("CONFIRMED");

        int cleared = cleanupService.clearExpiredContacts();

        assertThat(cleared).isZero();
        assertThat(contactOf(recruiting)).isEqualTo(CONTACT);
        assertThat(contactOf(confirmed)).isEqualTo(CONTACT);
    }

    @Test
    @DisplayName("[BC-23][PV-13] 이미 지운 행은 다시 건드리지 않아 두 번 돌려도 0건이다")
    void secondRunClearsNothing() {
        completedBasecampWithContact(
                Basecamp.CONTACT_VISIBLE_AFTER_COMPLETION.plusDays(1).toSeconds());

        assertThat(cleanupService.clearExpiredContacts()).isEqualTo(1);
        assertThat(cleanupService.clearExpiredContacts()).isZero();
    }

    @Test
    @DisplayName("[BC-23][PV-13] 여러 묶음에 걸친 취소·옛 완료 베이스캠프의 연락 수단을 모두 지우고, 모집 중이거나 최근 완료한 것은 남긴다")
    void clearsAcrossBatchesAndKeepsOthers() {
        long canceledTemplate = basecampWithContact("CANCELED");
        long completedTemplate = completedBasecampWithContact(
                Basecamp.CONTACT_VISIBLE_AFTER_COMPLETION.plusDays(1).toSeconds());
        long recruiting = basecampWithContact("RECRUITING");
        long recentlyCompleted = completedBasecampWithContact(
                Basecamp.CONTACT_VISIBLE_AFTER_COMPLETION.minusDays(1).toSeconds());
        copyBasecamps(canceledTemplate, 1_100);
        copyBasecamps(completedTemplate, 600);

        int cleared = cleanupService.clearExpiredContacts();

        assertThat(cleared).isEqualTo(1_702);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM basecamp WHERE contact_info IS NOT NULL", Integer.class))
                .isEqualTo(2);
        assertThat(contactOf(recruiting)).isEqualTo(CONTACT);
        assertThat(contactOf(recentlyCompleted)).isEqualTo(CONTACT);
    }

    private void copyBasecamps(long templateId, int count) {
        jdbc.update(
                "INSERT /*+ SET_VAR(cte_max_recursion_depth=2000) */ INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity,"
                        + " status, contact_info, completed_at, created_at, updated_at)"
                        + " WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ?)"
                        + " SELECT b.leader_id, b.spot_id, b.title, b.description, b.start_date, b.end_date,"
                        + " b.capacity, b.status, b.contact_info, b.completed_at, b.created_at, b.updated_at"
                        + " FROM basecamp b JOIN seq WHERE b.id = ?",
                count,
                templateId);
    }

    private long completedBasecampWithContact(long secondsAgo) {
        long id = basecampWithContact("COMPLETED");
        jdbc.update(
                "UPDATE basecamp SET completed_at = ? WHERE id = ?",
                LocalDateTime.ofInstant(NOW.minusSeconds(secondsAgo), ZoneOffset.UTC),
                id);
        return id;
    }

    private long basecampWithContact(String status) {
        long id = fixture.saveBasecamp(status);
        jdbc.update("UPDATE basecamp SET contact_info = ? WHERE id = ?", CONTACT, id);
        return id;
    }

    private String contactOf(long basecampId) {
        return jdbc.queryForObject("SELECT contact_info FROM basecamp WHERE id = ?", String.class, basecampId);
    }
}
