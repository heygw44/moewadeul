package com.moewadeul.spot.api.detail;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"server.tomcat.threads.max=5", "server.tomcat.threads.min-spare=5"})
@Import(SpotWeatherHttpIntegrationTest.DispatchLogConfig.class)
class SpotWeatherHttpIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final int KMA_DELAY_MILLIS = 400;
    private static final int WEATHER_REQUESTS = 40;

    private final HttpClient http =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void captureLogs() {
        logs.start();
        rootLogger().addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger().detachAppender(logs);
        logs.stop();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청이 느려 날씨 요청이 Tomcat 스레드 수보다 많이 몰려도, DB만 읽는 장소 상세는 바로 응답한다")
    void answersDbOnlyRequestWhileWeatherRequestsWaitForKma() throws Exception {
        // given
        long spotId = insertCampsite();
        stubKma(okJson(readFixture("kma/vilage-fcst.json")).withFixedDelay(KMA_DELAY_MILLIS), KMA_DELAY_MILLIS);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));
        assertThat(send("/api/spots/" + spotId, null).statusCode()).isEqualTo(200);
        List<CompletableFuture<HttpResponse<String>>> weatherRequests = IntStream.range(0, WEATHER_REQUESTS)
                .mapToObj(i -> http.sendAsync(
                        request("/api/spots/" + spotId + "/weather", null), HttpResponse.BodyHandlers.ofString()))
                .toList();
        Thread.sleep(300);
        boolean weatherStillWaiting = weatherRequests.stream().noneMatch(CompletableFuture::isDone);

        // when
        long startedAt = System.nanoTime();
        HttpResponse<String> detail = send("/api/spots/" + spotId, null);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // then
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(elapsed).isLessThan(Duration.ofMillis(KMA_DELAY_MILLIS));
        assertThat(weatherStillWaiting)
                .as("상세를 요청한 시점에 날씨 요청이 아직 기상청을 기다리는 중이다")
                .isTrue();
        for (CompletableFuture<HttpResponse<String>> weatherRequest : weatherRequests) {
            HttpResponse<String> response = weatherRequest.get(30, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(jsonMapper
                            .readTree(response.body())
                            .path("weather")
                            .path("source")
                            .asString())
                    .isEqualTo("기상청");
        }
    }

    @Test
    @DisplayName("[F-09][NFR-05] 로그인하지 않은 사용자의 날씨 요청은 비동기 재디스패치를 거쳐도 200이고, 세션을 새로 만들지 않으며 추적 ID 헤더는 하나다")
    void anonymousWeatherRequestSurvivesAsyncDispatchWithoutSession() throws Exception {
        // given
        long spotId = insertCampsite();
        stubKma(okJson(readFixture("kma/vilage-fcst.json")), 0);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));
        int sessionsBefore = countSessions();

        // when
        HttpResponse<String> response = send("/api/spots/" + spotId + "/weather", null);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jsonMapper
                        .readTree(response.body())
                        .path("weather")
                        .path("sun")
                        .isObject())
                .isTrue();
        assertThat(response.headers().allValues(TRACE_HEADER)).hasSize(1);
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(cookie -> cookie.startsWith(SESSION_COOKIE));
        assertThat(countSessions()).isEqualTo(sessionsBefore);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 로그인한 사용자의 날씨 요청도 비동기 재디스패치를 거쳐 200이고, 기존 세션을 그대로 쓰며 추적 ID 헤더는 하나다")
    void loggedInWeatherRequestSurvivesAsyncDispatchWithSameSession() throws Exception {
        // given
        long spotId = insertCampsite();
        stubKma(okJson(readFixture("kma/vilage-fcst.json")), 0);
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));
        String cookie = login();
        int sessionsBefore = countSessions();

        // when
        HttpResponse<String> response = send("/api/spots/" + spotId + "/weather", cookie);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jsonMapper
                        .readTree(response.body())
                        .path("weather")
                        .path("source")
                        .asString())
                .isEqualTo("기상청");
        assertThat(response.headers().allValues(TRACE_HEADER)).hasSize(1);
        assertThat(countSessions()).isEqualTo(sessionsBefore);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 가상 스레드에서 남긴 로그와 비동기 재디스패치에서 남긴 로그에 응답 헤더와 같은 추적 ID가 찍힌다")
    void sameTraceIdAppearsInVirtualThreadAndAsyncDispatchLogs() throws Exception {
        // given
        long spotId = insertCampsite();
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH)).willReturn(serverError()));

        // when
        HttpResponse<String> response = send("/api/spots/" + spotId + "/weather", null);

        // then
        String traceId = response.headers().firstValue(TRACE_HEADER).orElseThrow();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(logsOf(traceId))
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).isEqualTo("dispatch REQUEST"))
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).isEqualTo("dispatch ASYNC"))
                .anySatisfy(event -> {
                    assertThat(event.getFormattedMessage()).contains("기상청 날씨를 받지 못해");
                    assertThat(event.getThreadName()).startsWith("weather-");
                });
    }

    @Test
    @DisplayName("[F-09] Swagger 문서는 날씨 응답 형식을 CompletableFuture가 아니라 SpotWeatherResponse로 보여 준다")
    void apiDocsShowSpotWeatherResponseSchema() throws Exception {
        // when
        HttpResponse<String> docs = send("/v3/api-docs", null);

        // then
        JsonNode ok = jsonMapper
                .readTree(docs.body())
                .path("paths")
                .path("/api/spots/{spotId}/weather")
                .path("get")
                .path("responses")
                .path("200");
        assertThat(ok.toString()).contains("SpotWeatherResponse").doesNotContain("CompletableFuture");
    }

    private List<ILoggingEvent> logsOf(String traceId) {
        return List.copyOf(logs.list).stream()
                .filter(event -> traceId.equals(event.getMDCPropertyMap().get("traceId")))
                .toList();
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private HttpResponse<String> send(String path, String cookie) throws Exception {
        return http.send(request(path, cookie), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String path, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return builder.GET().build();
    }

    private int countSessions() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
    }

    private String login() throws Exception {
        String email = TestSequence.email();
        memberRepository.save(aMember()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .build());
        String csrfToken = UUID.randomUUID().toString();
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(SESSION_COOKIE + "="))
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SESSION 쿠키가 없다"));
    }

    private long insertCampsite() {
        clock.setInstant(MutableClock.DEFAULT_INSTANT);
        String name = TestSequence.unique("야영장");
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_GeomFromText('POINT(37.5665 126.978)', 4326), 60, 127, 'ACTIVE',"
                        + " NOW(6), NOW(6))",
                name);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private void stubKma(ResponseDefinitionBuilder shortTerm, int midTermDelayMillis) {
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH)).willReturn(shortTerm));
        wireMock.stubFor(get(urlPathEqualTo(MID_LAND_FCST_PATH))
                .willReturn(okJson(readFixture("kma/mid-land-fcst.json")).withFixedDelay(midTermDelayMillis)));
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH))
                .willReturn(okJson(readFixture("kma/mid-ta.json")).withFixedDelay(midTermDelayMillis)));
    }

    private void stubKasi(ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(RISE_SET_PATH)).willReturn(response));
    }

    private static ResponseDefinitionBuilder okXml(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml;charset=UTF-8")
                .withBody(body);
    }

    private static String readFixture(String path) {
        try {
            return new ClassPathResource("fixtures/weather/" + path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Slf4j
    @TestConfiguration(proxyBeanMethods = false)
    static class DispatchLogConfig {

        @Bean
        OncePerRequestFilter dispatchLogFilter() {
            return new OncePerRequestFilter() {
                @Override
                protected boolean shouldNotFilterAsyncDispatch() {
                    return false;
                }

                @Override
                protected void doFilterInternal(
                        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                        throws ServletException, IOException {
                    DispatcherType type = request.getDispatcherType();
                    log.info("dispatch {}", type);
                    filterChain.doFilter(request, response);
                }
            };
        }
    }
}
