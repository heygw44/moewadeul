package com.moewadeul.admin.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.memory.application.MemoryReportCommand;
import com.moewadeul.memory.application.MemoryReportService;
import com.moewadeul.trust.application.CompanionReviewFixture;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class AdminMemoryReviewApiIntegrationTest {

    private static final String MEMORIES = "/api/admin/memories";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String SECRET_CONTENT = "비밀신고내용이들어간문장";
    private static final String MEMORY_CONTENT = "기록 글 전체";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemoryReportService memoryReportService;

    private CompanionReviewFixture fixture;
    private long adminId;
    private Cookie adminSession;
    private long authorId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", adminId);
        adminSession = login(adminId);
        authorId = fixture.saveVerifiedMember("기록쓴이");
    }

    @Test
    @DisplayName("[F-33][MM-11] 검토 대기 기록은 작성자, 대략 좌표, 글, 검토 전 신고 수, 사유별 수, 최근 신고 5건과 함께 나오고 정확한 좌표와 신고자는 없다")
    void listShowsPendingMemoryWithReports() {
        // given
        long pending = insertMemory("PUBLIC", "PENDING_REVIEW", BASE);
        for (int i = 1; i <= 6; i++) {
            insertReport(
                    pending, i % 2 == 0 ? "ABUSE" : "SPAM", SECRET_CONTENT + i, BASE.plus(Duration.ofHours(i)), null);
        }
        insertReport(pending, "PRIVACY", "검토를 마친 신고", BASE, BASE.plus(Duration.ofDays(1)));
        insertMemory("PUBLIC", "ACTIVE", BASE);

        // when
        MvcTestResult result = get(MEMORIES);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(ids(result, "$.content[*].memoryId")).containsExactly(pending);
        assertThat(result).bodyJson().extractingPath("$.content[0].content").isEqualTo(MEMORY_CONTENT);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo("기록쓴이");
        assertThat(result).bodyJson().extractingPath("$.content[0].approxLat").isEqualTo(37.57);
        assertThat(result).bodyJson().extractingPath("$.content[0].approxLng").isEqualTo(127.12);
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].lat");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].latitude");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].images")
                .asArray()
                .isEmpty();
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("PENDING_REVIEW");
        assertThat(result).bodyJson().extractingPath("$.content[0].reportCount").isEqualTo(6);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.SPAM")
                .isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.PRIVACY")
                .isEqualTo(0);
        List<String> recent = json(result, "$.content[0].recentReports[*].content");
        assertThat(recent)
                .containsExactly(
                        SECRET_CONTENT + "6",
                        SECRET_CONTENT + "5",
                        SECRET_CONTENT + "4",
                        SECRET_CONTENT + "3",
                        SECRET_CONTENT + "2");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].recentReports[0].reporterId");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-33][MM-11] 금전 요구·사기 신고가 있는 기록이 먼저 나오고, 같은 묶음에서는 상태가 바뀐 시각이 이른 기록부터 나오며, 비공개 기록은 빠진다")
    void listPutsMoneyScamFirstAndExcludesPrivate() {
        // given
        long plainOld = insertMemory("PUBLIC", "PENDING_REVIEW", BASE);
        long scamNew = insertMemory("PUBLIC", "PENDING_REVIEW", BASE.plus(Duration.ofHours(5)));
        long plainNew = insertMemory("PUBLIC", "PENDING_REVIEW", BASE.plus(Duration.ofHours(1)));
        long privatePending = insertMemory("PRIVATE", "PENDING_REVIEW", BASE);
        insertReport(scamNew, "MONEY_SCAM", null, BASE, null);
        insertReport(privatePending, "MONEY_SCAM", null, BASE, null);

        // when
        MvcTestResult all = get(MEMORIES);
        MvcTestResult first = get(MEMORIES + "?size=2");
        MvcTestResult second = get(MEMORIES + "?size=2&page=1");

        // then
        assertThat(ids(all, "$.content[*].memoryId")).containsExactly(scamNew, plainOld, plainNew);
        assertThat(ids(first, "$.content[*].memoryId")).containsExactly(scamNew, plainOld);
        assertThat(first).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(ids(second, "$.content[*].memoryId")).containsExactly(plainNew);
        assertThat(second).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-33][MM-11] status=HIDDEN이면 숨긴 기록만 나오고, 허용하지 않는 status와 범위를 벗어난 size는 400 INVALID_INPUT이다")
    void listFiltersByStatusAndValidatesInput() {
        // given
        long hidden = insertMemory("PUBLIC", "HIDDEN", BASE);
        insertMemory("PUBLIC", "PENDING_REVIEW", BASE);

        // when // then
        assertThat(ids(get(MEMORIES + "?status=HIDDEN"), "$.content[*].memoryId"))
                .containsExactly(hidden);
        for (String query : List.of("?status=ACTIVE", "?status=hidden", "?size=51", "?size=0")) {
            MvcTestResult result = get(MEMORIES + query);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
    }

    @Test
    @DisplayName("[F-33][MM-11] 검토 대기 기록을 숨기면 HIDDEN이 되고 감사 로그 한 줄이 남으며 detail에 글과 신고 내용이 없다")
    void hideWritesAuditLog() {
        // given
        long memoryId = insertMemory("PUBLIC", "PENDING_REVIEW", BASE);
        insertReport(memoryId, "SPAM", SECRET_CONTENT, BASE, null);

        // when
        MvcTestResult result = post(MEMORIES + "/" + memoryId + "/hide");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{\"memoryId\":" + memoryId + ",\"status\":\"HIDDEN\"}");
        assertThat(memoryStatus(memoryId)).isEqualTo("HIDDEN");
        assertThat(mvc.get().uri("/api/memories/" + memoryId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(admin_id, ':', target_type, ':', target_id) FROM admin_audit_log"
                                + " WHERE action = 'MEMORY_HIDE'",
                        String.class))
                .isEqualTo(adminId + ":MEMORY:" + memoryId);
        String detail =
                jdbc.queryForObject("SELECT detail FROM admin_audit_log WHERE action = 'MEMORY_HIDE'", String.class);
        assertThat((String) JsonPath.read(detail, "$.fromStatus")).isEqualTo("PENDING_REVIEW");
        assertThat((String) JsonPath.read(detail, "$.toStatus")).isEqualTo("HIDDEN");
        assertThat(detail).doesNotContain(SECRET_CONTENT).doesNotContain(MEMORY_CONTENT);
    }

    @Test
    @DisplayName("[F-33][MM-11] 검토 대기 기록을 복구하면 ACTIVE가 되어 남에게 다시 보이고 검토 전 신고가 검토 완료로 표시되며 감사 로그에 건수가 남는다")
    void restoreMarksReportsReviewed() {
        // given
        long memoryId = insertMemory("PUBLIC", "PENDING_REVIEW", BASE);
        for (int i = 0; i < 5; i++) {
            insertReport(memoryId, "SPAM", SECRET_CONTENT, BASE.plus(Duration.ofMinutes(i)), null);
        }

        // when
        MvcTestResult result = post(MEMORIES + "/" + memoryId + "/restore");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(mvc.get().uri("/api/memories/" + memoryId).exchange()).hasStatus(HttpStatus.OK);
        assertThat(count("memory_report WHERE memory_id = " + memoryId + " AND reviewed_at IS NULL"))
                .isZero();
        String detail =
                jdbc.queryForObject("SELECT detail FROM admin_audit_log WHERE action = 'MEMORY_RESTORE'", String.class);
        assertThat((String) JsonPath.read(detail, "$.fromStatus")).isEqualTo("PENDING_REVIEW");
        assertThat((String) JsonPath.read(detail, "$.toStatus")).isEqualTo("ACTIVE");
        assertThat((Integer) JsonPath.read(detail, "$.reviewedReportCount")).isEqualTo(5);
    }

    @Test
    @DisplayName("[F-33][MM-10][MM-11] 복구한 기록은 새 신고 1건으로는 검토 대기가 되지 않고 새 신고 5건이면 다시 검토 대기가 된다")
    void afterRestoreOnlyNewReportsCount() {
        // given
        long memoryId = insertMemory("PUBLIC", "PENDING_REVIEW", BASE);
        for (int i = 0; i < 5; i++) {
            insertReport(memoryId, "SPAM", null, BASE.plus(Duration.ofMinutes(i)), null);
        }
        assertThat(post(MEMORIES + "/" + memoryId + "/restore")).hasStatus(HttpStatus.OK);
        MemoryReportCommand report = new MemoryReportCommand("SPAM", "다시 확인했다");

        // when
        memoryReportService.report(newMemberId(), memoryId, report);

        // then
        assertThat(memoryStatus(memoryId)).isEqualTo("ACTIVE");
        for (int i = 0; i < 4; i++) {
            memoryReportService.report(newMemberId(), memoryId, report);
        }
        assertThat(memoryStatus(memoryId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("[F-33][MM-11] 신고가 없는 정상 기록도 바로 숨기고, 이미 숨긴 기록 숨김과 정상 기록 복구는 409, 비공개·없는 기록 숨김은 404다")
    void invalidStateAndNotFound() {
        // given
        long active = insertMemory("PUBLIC", "ACTIVE", BASE);
        long hidden = insertMemory("PUBLIC", "HIDDEN", BASE);
        long privateActive = insertMemory("PRIVATE", "ACTIVE", BASE);
        long privateHidden = insertMemory("PRIVATE", "HIDDEN", BASE);

        // when
        MvcTestResult hideActive = post(MEMORIES + "/" + active + "/hide");
        MvcTestResult hideHidden = post(MEMORIES + "/" + hidden + "/hide");
        MvcTestResult restoreActive = post(MEMORIES + "/" + privateActive + "/restore");
        MvcTestResult hidePrivate = post(MEMORIES + "/" + privateActive + "/hide");
        MvcTestResult hideMissing = post(MEMORIES + "/999999/hide");
        MvcTestResult restorePrivateHidden = post(MEMORIES + "/" + privateHidden + "/restore");

        // then
        assertThat(hideActive).hasStatus(HttpStatus.OK);
        assertThat(memoryStatus(active)).isEqualTo("HIDDEN");
        assertThat(hideHidden).hasStatus(HttpStatus.CONFLICT);
        assertThat(hideHidden).bodyJson().extractingPath("$.code").isEqualTo("MEMORY_INVALID_STATE");
        assertThat(restoreActive).hasStatus(HttpStatus.CONFLICT);
        assertThat(restoreActive).bodyJson().extractingPath("$.code").isEqualTo("MEMORY_INVALID_STATE");
        assertThat(hidePrivate).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(hideMissing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(restorePrivateHidden).hasStatus(HttpStatus.OK);
        assertThat(memoryStatus(privateHidden)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-33][MM-11] 관리자가 아닌 회원은 목록, 숨김, 복구에서 403 ACCESS_DENIED이고 로그인하지 않으면 401이다")
    void requiresAdmin() {
        // given
        long memoryId = insertMemory("PUBLIC", "PENDING_REVIEW", BASE);
        Cookie user = login(fixture.saveVerifiedMember(TestSequence.nickname()));

        // when
        MvcTestResult anonymous = mvc.get().uri(MEMORIES).exchange();
        MvcTestResult list = mvc.get().uri(MEMORIES).cookie(user).exchange();
        MvcTestResult hide = mvc.post()
                .uri(MEMORIES + "/" + memoryId + "/hide")
                .cookie(user)
                .with(csrf())
                .exchange();
        MvcTestResult restore = mvc.post()
                .uri(MEMORIES + "/" + memoryId + "/restore")
                .cookie(user)
                .with(csrf())
                .exchange();

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        for (MvcTestResult result : List.of(list, hide, restore)) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        }
        assertThat(memoryStatus(memoryId)).isEqualTo("PENDING_REVIEW");
    }

    private long insertMemory(String visibility, String status, Instant updatedAt) {
        jdbc.update(
                "INSERT INTO memory (member_id, latitude, longitude, visited_date, nights, content, visibility, status,"
                        + " created_at, updated_at) VALUES (?, 37.56789, 127.12345, ?, 1, ?, ?, ?, ?, ?)",
                authorId,
                LocalDate.of(2026, 9, 30),
                MEMORY_CONTENT,
                visibility,
                status,
                BASE,
                updatedAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM memory", Long.class);
    }

    private void insertReport(long memoryId, String reason, String content, Instant createdAt, Instant reviewedAt) {
        jdbc.update(
                "INSERT INTO memory_report (memory_id, reporter_id, reason, content, created_at, reviewed_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                memoryId,
                newMemberId(),
                reason,
                content,
                createdAt,
                reviewedAt);
    }

    private long newMemberId() {
        return fixture.saveVerifiedMember(TestSequence.nickname());
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).cookie(adminSession).exchange();
    }

    private MvcTestResult post(String uri) {
        return mvc.post().uri(uri).cookie(adminSession).with(csrf()).exchange();
    }

    private static <T> T json(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    private static List<Long> ids(MvcTestResult result, String path) {
        List<Number> numbers = json(result, path);
        return numbers.stream().map(Number::longValue).toList();
    }

    private String memoryStatus(long memoryId) {
        return jdbc.queryForObject("SELECT status FROM memory WHERE id = ?", String.class, memoryId);
    }

    private int count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private Cookie login(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }
}
