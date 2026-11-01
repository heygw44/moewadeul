package com.moewadeul.common.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.community.application.report.CommunityReportContentCleanupService;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.application.report.MemoryReportContentCleanupService;
import com.moewadeul.spot.application.bakji.BakjiReportContentCleanupService;
import com.moewadeul.trust.application.CompanionReviewFixture;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ContentReportContentCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private CommunityReportContentCleanupService communityCleanup;

    @Autowired
    private MemoryReportContentCleanupService memoryCleanup;

    @Autowired
    private BakjiReportContentCleanupService bakjiCleanup;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private CompanionReviewFixture fixture;
    private long targetId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @ParameterizedTest
    @EnumSource(ContentReportKind.class)
    @DisplayName("[F-21][PV-07] 검토를 마친 지 1년이 지난 콘텐츠 신고는 내용을 지우고 행은 남긴다")
    void clearsReviewedContentReportsOlderThanOneYear(ContentReportKind kind) {
        long target = contentTarget(kind, "ACTIVE", cutoff().minus(Duration.ofDays(400)));
        long report = contentReport(kind, target, cutoff().minus(Duration.ofDays(30)));

        int result = kind.cleanup(this).clearExpiredContent();

        assertThat(result).isEqualTo(1);
        assertThat(contentErased(kind, report)).isTrue();
        assertThat(count(kind.reportTable + " WHERE id = ?", report)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(ContentReportKind.class)
    @DisplayName("[F-21][PV-07] 정확히 1년 전에 검토를 마친 콘텐츠 신고는 지우고, 1년이 되기 1초 전인 신고는 남긴다")
    void clearsReviewedContentReportAtCutoffAndKeepsOneSecondLater(ContentReportKind kind) {
        long exactTarget = contentTarget(kind, "ACTIVE", cutoff().minus(Duration.ofDays(400)));
        long exact = contentReport(kind, exactTarget, cutoff());
        long justUnderTarget = contentTarget(kind, "ACTIVE", cutoff().minus(Duration.ofDays(400)));
        long justUnder = contentReport(kind, justUnderTarget, cutoff().plus(ONE_SECOND));

        int result = kind.cleanup(this).clearExpiredContent();

        assertThat(result).isEqualTo(1);
        assertThat(contentErased(kind, exact)).isTrue();
        assertThat(contentErased(kind, justUnder)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ContentReportKind.class)
    @DisplayName("[F-21][PV-07] 검토 전 신고라도 대상을 숨긴 지 1년이 지났으면 내용을 지우고, 1년이 되기 1초 전에 숨겼으면 남긴다")
    void clearsUnreviewedReportsOfLongHiddenTarget(ContentReportKind kind) {
        long older = contentReport(kind, contentTarget(kind, "HIDDEN", cutoff().minus(Duration.ofDays(30))), null);
        long exact = contentReport(kind, contentTarget(kind, "HIDDEN", cutoff()), null);
        long justUnder = contentReport(kind, contentTarget(kind, "HIDDEN", cutoff().plus(ONE_SECOND)), null);

        int result = kind.cleanup(this).clearExpiredContent();

        assertThat(result).isEqualTo(2);
        assertThat(contentErased(kind, older)).isTrue();
        assertThat(contentErased(kind, exact)).isTrue();
        assertThat(contentErased(kind, justUnder)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ContentReportKind.class)
    @DisplayName("[F-21][PV-07] 검토 전 신고가 걸린 대상이 숨김이 아니면 신고를 접수한 지 오래돼도 내용을 지우지 않는다")
    void keepsUnreviewedReportsOfTargetsThatAreNotHidden(ContentReportKind kind) {
        Instant longAgo = cutoff().minus(Duration.ofDays(400));
        for (String status : kind.nonHiddenStatuses) {
            long report = contentReport(kind, contentTarget(kind, status, longAgo), null);

            int result = kind.cleanup(this).clearExpiredContent();

            assertThat(result).as(status).isZero();
            assertThat(contentErased(kind, report)).as(status).isFalse();
        }
    }

    private Instant cutoff() {
        return clock.instant()
                .atZone(ZoneOffset.UTC)
                .minus(ReportContentCleanup.RETENTION)
                .toInstant();
    }

    private long contentTarget(ContentReportKind kind, String status, Instant updatedAt) {
        String updated = DATETIME_UTC.format(updatedAt);
        switch (kind) {
            case COMMUNITY_POST -> {
                return insertPost(status, updated);
            }
            case COMMUNITY_COMMENT -> {
                jdbc.update(
                        "INSERT INTO community_comment (post_id, member_id, content, status, created_at, updated_at)"
                                + " VALUES (?, ?, '댓글', ?, ?, ?)",
                        insertPost("ACTIVE", updated),
                        targetId,
                        status,
                        updated,
                        updated);
                return jdbc.queryForObject("SELECT MAX(id) FROM community_comment", Long.class);
            }
            case MEMORY -> {
                jdbc.update(
                        "INSERT INTO memory (member_id, latitude, longitude, visited_date, nights, content, visibility,"
                                + " status, created_at, updated_at) VALUES (?, 37.56789, 127.12345, ?, 1, '기록', 'PRIVATE', ?, ?, ?)",
                        targetId,
                        LocalDate.of(2026, 9, 30),
                        status,
                        updated,
                        updated);
                return jdbc.queryForObject("SELECT MAX(id) FROM memory", Long.class);
            }
            case BAKJI -> {
                jdbc.update(
                        "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                                + " VALUES ('BAKJI', ?, ST_SRID(POINT(127.25, 37.25), 4326), 60, 127, ?, ?, ?)",
                        TestSequence.unique("박지"),
                        status,
                        updated,
                        updated);
                return jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
            }
        }
        throw new IllegalStateException(kind.name());
    }

    private long insertPost(String status, String updated) {
        jdbc.update(
                "INSERT INTO community_post (member_id, title, content, status, created_at, updated_at)"
                        + " VALUES (?, '제목', '본문', ?, ?, ?)",
                targetId,
                status,
                updated,
                updated);
        return jdbc.queryForObject("SELECT MAX(id) FROM community_post", Long.class);
    }

    private long contentReport(ContentReportKind kind, long contentId, Instant reviewedAt) {
        long reporterId = fixture.saveVerifiedMember(TestSequence.nickname());
        String created = DATETIME_UTC.format(cutoff().minus(Duration.ofDays(400)));
        String reviewed = reviewedAt == null ? null : DATETIME_UTC.format(reviewedAt);
        switch (kind) {
            case COMMUNITY_POST, COMMUNITY_COMMENT ->
                jdbc.update(
                        "INSERT INTO community_report (target_type, target_id, reporter_id, reason, content, reviewed_at,"
                                + " created_at) VALUES (?, ?, ?, 'OTHER', '신고 내용', ?, ?)",
                        kind == ContentReportKind.COMMUNITY_POST ? "POST" : "COMMENT",
                        contentId,
                        reporterId,
                        reviewed,
                        created);
            case MEMORY ->
                jdbc.update(
                        "INSERT INTO memory_report (memory_id, reporter_id, reason, content, reviewed_at, created_at)"
                                + " VALUES (?, ?, 'OTHER', '신고 내용', ?, ?)",
                        contentId,
                        reporterId,
                        reviewed,
                        created);
            case BAKJI ->
                jdbc.update(
                        "INSERT INTO bakji_report (spot_id, reporter_id, reason, content, reviewed_at, created_at)"
                                + " VALUES (?, ?, 'OTHER', '신고 내용', ?, ?)",
                        contentId,
                        reporterId,
                        reviewed,
                        created);
        }
        return jdbc.queryForObject("SELECT MAX(id) FROM " + kind.reportTable, Long.class);
    }

    private boolean contentErased(ContentReportKind kind, long reportId) {
        return count(kind.reportTable + " WHERE id = ? AND content IS NULL", reportId) == 1;
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }

    private enum ContentReportKind {
        COMMUNITY_POST("community_report", test -> test.communityCleanup, "ACTIVE", "PENDING_REVIEW", "DELETED"),
        COMMUNITY_COMMENT("community_report", test -> test.communityCleanup, "ACTIVE", "PENDING_REVIEW", "DELETED"),
        MEMORY("memory_report", test -> test.memoryCleanup, "ACTIVE", "PENDING_REVIEW"),
        BAKJI("bakji_report", test -> test.bakjiCleanup, "ACTIVE", "PENDING_REVIEW", "DELETED");

        private final String reportTable;
        private final Function<ContentReportContentCleanupIntegrationTest, ReportContentCleanup> cleanup;
        private final String[] nonHiddenStatuses;

        ContentReportKind(
                String reportTable,
                Function<ContentReportContentCleanupIntegrationTest, ReportContentCleanup> cleanup,
                String... nonHiddenStatuses) {
            this.reportTable = reportTable;
            this.cleanup = cleanup;
            this.nonHiddenStatuses = nonHiddenStatuses;
        }

        ReportContentCleanup cleanup(ContentReportContentCleanupIntegrationTest test) {
            return cleanup.apply(test);
        }
    }
}
