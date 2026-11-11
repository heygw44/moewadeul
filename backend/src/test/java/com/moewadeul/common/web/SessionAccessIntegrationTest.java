package com.moewadeul.common.web;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.mysql.MySQLContainer;

@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SessionAccessIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final int CAMPSITE_COUNT = 300;
    private static final int RESPONSE_BUFFER_BYTES = 8 * 1024;
    private static final long MISSING_ID = 999_999_999L;
    private static final Duration SETTLE_DEADLINE = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 20;
    private static final long SETTLE_HOLD_MILLIS = 200;

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MySQLContainer mysql;

    @Test
    @DisplayName("[F-03] 로그인한 회원이 지도 영역을 조회해도 서버는 세션을 읽거나 저장하지 않는다")
    void publicMapRequestDoesNotTouchSession() throws Exception {
        // given
        for (int i = 0; i < CAMPSITE_COUNT; i++) {
            insertCampsite("영역 안 야영장 " + i, 37.1 + i * 0.002, 127.1 + i * 0.002);
        }
        String cookie = login();
        SessionSql before = sessionSql();

        // when
        HttpResponse<String> response = get("/api/spots?swLat=37&swLng=127&neLat=38&neLng=128&zoom=7", cookie);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().length()).isGreaterThan(RESPONSE_BUFFER_BYTES);
        assertThat(settledSessionSql(before, 0, 0)).isEqualTo(new SessionSql(0, 0));
    }

    @Test
    @DisplayName("[F-02] 로그인 정보가 필요한 요청은 세션을 한 번 읽고, 저장된 마지막 접근 시각이 60초 안이면 저장하지 않는다")
    void authenticatedRequestReadsSessionOnceAndDoesNotSave() throws Exception {
        // given
        String cookie = login();
        SessionSql before = sessionSql();

        // when
        HttpResponse<String> response = get("/api/me", cookie);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(settledSessionSql(before, 1, 0)).isEqualTo(new SessionSql(1, 0));
    }

    @Test
    @DisplayName("[F-02] 로그인 정보가 필요한 요청이 오류 응답으로 끝나도 세션을 한 번만 읽는다")
    void authenticatedErrorResponseReadsSessionOnce() throws Exception {
        // given
        String cookie = login();
        SessionSql before = sessionSql();

        // when
        HttpResponse<String> response = get("/api/basecamps/" + MISSING_ID + "/applications", cookie);

        // then
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("NOT_FOUND");
        assertThat(settledSessionSql(before, 1, 0)).isEqualTo(new SessionSql(1, 0));
    }

    @Test
    @DisplayName("[F-02] 저장된 마지막 접근 시각이 60초보다 오래됐으면 요청이 끝날 때 그 시각을 새로 저장한다")
    void staleLastAccessTimeIsWrittenAgain() throws Exception {
        // given
        String cookie = login();
        moveStoredAccessTimeBack(cookie, Duration.ofSeconds(61));
        SessionSql before = sessionSql();
        long requestedAt = System.currentTimeMillis();

        // when
        HttpResponse<String> response = get("/api/me", cookie);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(settledSessionSql(before, 1, 1)).isEqualTo(new SessionSql(1, 1));
        assertThat(storedAccessTime(cookie)).isGreaterThanOrEqualTo(requestedAt);
    }

    @Test
    @DisplayName("[F-02] 저장된 마지막 접근 시각이 60초 안이면 요청이 끝나도 그 시각을 저장하지 않는다")
    void recentLastAccessTimeIsLeftAlone() throws Exception {
        // given
        String cookie = login();
        long stored = moveStoredAccessTimeBack(cookie, Duration.ofSeconds(30));
        SessionSql before = sessionSql();

        // when
        HttpResponse<String> response = get("/api/me", cookie);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(settledSessionSql(before, 1, 0)).isEqualTo(new SessionSql(1, 0));
        assertThat(storedAccessTime(cookie)).isEqualTo(stored);
    }

    private long moveStoredAccessTimeBack(String cookie, Duration amount) {
        jdbc.update(
                "UPDATE SPRING_SESSION SET LAST_ACCESS_TIME = LAST_ACCESS_TIME - ?, EXPIRY_TIME = EXPIRY_TIME - ? WHERE SESSION_ID = ?",
                amount.toMillis(),
                amount.toMillis(),
                sessionId(cookie));
        return storedAccessTime(cookie);
    }

    private long storedAccessTime(String cookie) {
        return jdbc.queryForObject(
                "SELECT LAST_ACCESS_TIME FROM SPRING_SESSION WHERE SESSION_ID = ?", Long.class, sessionId(cookie));
    }

    private static String sessionId(String cookie) {
        String value = cookie.substring(SESSION_COOKIE.length() + 1);
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private record SessionSql(long selects, long updates) {

        SessionSql minus(SessionSql other) {
            return new SessionSql(selects - other.selects, updates - other.updates);
        }
    }

    // 응답을 일찍 커밋한 요청은 클라이언트가 본문을 받은 뒤에도 필터의 마지막 커밋이 남아 있을 수 있다.
    private SessionSql settledSessionSql(SessionSql before, long expectedSelects, long expectedUpdates)
            throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + SETTLE_DEADLINE.toNanos();
        SessionSql delta = sessionSql().minus(before);
        while ((delta.selects < expectedSelects || delta.updates < expectedUpdates) && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MILLIS);
            delta = sessionSql().minus(before);
        }
        Thread.sleep(SETTLE_HOLD_MILLIS);
        return sessionSql().minus(before);
    }

    private SessionSql sessionSql() throws SQLException {
        try (Connection root = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement statement = root.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COALESCE(SUM(CASE WHEN DIGEST_TEXT LIKE 'SELECT %FROM `SPRING_SESSION` `S` LEFT JOIN%WHERE `S` . `SESSION_ID` = ?' THEN COUNT_STAR END), 0), "
                                + "COALESCE(SUM(CASE WHEN DIGEST_TEXT LIKE 'UPDATE `SPRING_SESSION` SET `SESSION_ID` = ?%' THEN COUNT_STAR END), 0) "
                                + "FROM performance_schema.events_statements_summary_by_digest "
                                + "WHERE DIGEST_TEXT LIKE '%`SPRING_SESSION`%'")) {
            rows.next();
            return new SessionSql(rows.getLong(1), rows.getLong(2));
        }
    }

    private String login() throws Exception {
        String email = TestSequence.email();
        memberRepository.save(aMember()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .emailVerifiedAt(MutableClock.DEFAULT_INSTANT)
                .build());
        String csrfToken = UUID.randomUUID().toString();
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(uri("/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String session = response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(SESSION_COOKIE + "="))
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SESSION 쿠키가 없다"));
        return session;
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return http.send(
                HttpRequest.newBuilder(uri(path)).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private void insertCampsite(String name, double latitude, double longitude) {
        Instant now = MutableClock.DEFAULT_INSTANT;
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_SRID(POINT(?, ?), 4326), 60, 127, 'ACTIVE', ?, ?)",
                name,
                longitude,
                latitude,
                now,
                now);
    }
}
