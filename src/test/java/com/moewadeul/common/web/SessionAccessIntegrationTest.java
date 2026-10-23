package com.moewadeul.common.web;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// MockMvc는 요청을 처리한 뒤 결과를 모으면서 세션을 직접 연다. 그래서 운영처럼 서버가 세션을 건드리는지 보려면
// 실제 Tomcat을 띄우고 HTTP로 요청해야 한다.
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SessionAccessIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final int CAMPSITE_COUNT = 300;
    private static final int RESPONSE_BUFFER_BYTES = 8 * 1024;

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private JdbcIndexedSessionRepository sessionRepository;

    @Test
    @DisplayName("[F-03] 로그인한 회원이 지도 영역을 조회해도 서버는 세션을 읽거나 저장하지 않는다")
    void publicMapRequestDoesNotTouchSession() throws Exception {
        // given: 응답 본문이 커지도록 영역 안에 야영장을 여럿 넣고, 회원이 로그인해 세션을 받는다.
        for (int i = 0; i < CAMPSITE_COUNT; i++) {
            insertCampsite("영역 안 야영장 " + i, 37.1 + i * 0.002, 127.1 + i * 0.002);
        }
        String cookie = login();
        clearInvocations(sessionRepository);

        // when
        HttpResponse<String> response = get("/api/spots?swLat=37&swLng=127&neLat=38&neLng=128&zoom=7", cookie);

        // then: 본문이 Tomcat 응답 버퍼(8KB)보다 커서 응답이 일찍 커밋되는 경우다.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().length()).isGreaterThan(RESPONSE_BUFFER_BYTES);
        verify(sessionRepository, never()).findById(anyString());
        verify(savingRepository(), never()).save(any());
    }

    @Test
    @DisplayName("[F-02] 로그인 정보가 필요한 요청은 세션을 한 번 읽고 마지막 접근 시각을 한 번 저장한다")
    void authenticatedRequestReadsAndSavesSessionOnce() throws Exception {
        // given
        String cookie = login();
        clearInvocations(sessionRepository);

        // when
        HttpResponse<String> response = get("/api/me", cookie);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        verify(sessionRepository, times(1)).findById(anyString());
        verify(savingRepository(), times(1)).save(any());
    }

    // 세션 저장소의 save는 Spring Session 안쪽의 비공개 세션 타입을 받는다.
    // 그래서 그 타입을 쓰지 않고 호출을 확인하려고 일반 저장소 타입으로 바꿔 검증한다.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private SessionRepository<Session> savingRepository() {
        return (SessionRepository) sessionRepository;
    }

    // 로그인 응답의 세션 쿠키를 Cookie 헤더 값으로 돌려준다. 세션 쿠키에는 Secure가 붙어서 HttpClient가 http 주소로는
    // 쿠키를 다시 보내지 않으므로, 테스트가 헤더를 직접 만든다.
    private String login() throws Exception {
        String email = TestSequence.email();
        memberRepository.save(aMember()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
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
