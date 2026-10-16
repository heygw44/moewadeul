package com.moewadeul.spot.application.detail;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.weather.application.WeatherForecast.ShortTermForecast;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotWeatherQueryServiceIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
    private static final List<String> ALL_PATHS =
            List.of(VILAGE_FCST_PATH, MID_LAND_FCST_PATH, MID_TA_PATH, RISE_SET_PATH);

    // 한국 시각 2026-10-04 12:58. 날씨 픽스처를 받은 시각이고, 이때 조회할 단기예보 발표는 11시다.
    private static final Instant FIXTURE_TIME = Instant.parse("2026-10-04T03:58:00Z");

    @Autowired
    private SpotWeatherQueryService spotWeatherQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private WireMockServer wireMock;

    @Test
    @DisplayName("[F-09] 기상청과 천문연이 정상이면 단기·중기 예보, 출처, 오늘 한국 날짜의 출몰시각을 채운다")
    void fillsWeatherAndSunWhenBothApisSucceed() {
        // given
        long spotId = insertSeoulCampsite("ACTIVE");
        stubKmaSuccess();
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));

        // when
        Optional<SpotWeather> weather = spotWeatherQueryService.findWeather(spotId);

        // then
        assertThat(weather).isPresent();
        assertThat(weather.get().forecast().source()).isEqualTo("기상청");
        assertThat(weather.get().forecast().shortTerm()).isNotEmpty();
        assertThat(weather.get().forecast().midTerm()).hasSize(7);
        assertThat(weather.get().sun().date()).isEqualTo(LocalDate.parse("2026-10-04"));
        assertThat(weather.get().sun().sunrise()).isEqualTo(LocalTime.of(6, 30));
        wireMock.verify(getRequestedFor(urlPathEqualTo(RISE_SET_PATH)).withQueryParam("locdate", equalTo("20261004")));
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청이 500이면 빈 값을 돌려주고 천문연은 부르지 않는다")
    void returnsEmptyAndSkipsKasiWhenKmaFails() {
        // given
        long spotId = insertSeoulCampsite("ACTIVE");
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH)).willReturn(serverError()));
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));

        // when
        Optional<SpotWeather> weather = spotWeatherQueryService.findWeather(spotId);

        // then
        assertThat(weather).isEmpty();
        assertThat(requestCount(RISE_SET_PATH)).isZero();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청은 성공하고 천문연만 실패하면 예보는 채우고 sun만 null이다")
    void leavesSunNullWhenOnlyKasiFails() {
        // given
        long spotId = insertSeoulCampsite("ACTIVE");
        stubKmaSuccess();
        stubKasi(serverError());

        // when
        Optional<SpotWeather> weather = spotWeatherQueryService.findWeather(spotId);

        // then
        assertThat(weather).isPresent();
        assertThat(weather.get().forecast().shortTerm()).isNotEmpty();
        assertThat(weather.get().sun()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "DELETED", "PENDING_REVIEW"})
    @DisplayName("[F-09] 숨김, 삭제, 검토 대기 상태의 장소는 외부 API를 부르지 않고 NOT_FOUND로 거부한다")
    void rejectsInactiveSpotWithoutCallingApis(String status) {
        // given
        long spotId = insertSeoulCampsite(status);
        stubAllSuccess();

        // when, then
        assertThatThrownBy(() -> spotWeatherQueryService.findWeather(spotId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("[F-09] 없는 장소 ID는 외부 API를 부르지 않고 NOT_FOUND로 거부한다")
    void rejectsMissingSpotWithoutCallingApis() {
        // given
        stubAllSuccess();

        // when, then
        assertThatThrownBy(() -> spotWeatherQueryService.findWeather(Long.MAX_VALUE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("[F-09][NFR-06] 같은 장소를 같은 발표 시각에 두 번 조회하면 기상청·천문연은 오퍼레이션마다 한 번만 부른다")
    void callsEachApiOnceForSameSpotAndPublishTime() {
        // given
        long spotId = insertSeoulCampsite("ACTIVE");
        stubAllSuccess();
        SpotWeather first = spotWeatherQueryService.findWeather(spotId).orElseThrow();

        // when
        SpotWeather second = spotWeatherQueryService.findWeather(spotId).orElseThrow();

        // then
        assertThat(second).isEqualTo(first);
        for (String path : ALL_PATHS) {
            assertThat(requestCount(path)).as(path).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("[F-09][NFR-06] 같은 격자의 다른 장소도 기상청 단기예보를 다시 부르지 않고, 출몰시각은 장소마다 부른다")
    void sharesShortTermForecastBetweenSpotsOnSameGrid() {
        // given
        long firstSpot = insertSeoulCampsite("ACTIVE");
        long secondSpot = insertCampsite("ACTIVE", "POINT(37.5700 126.9800)");
        stubAllSuccess();
        spotWeatherQueryService.findWeather(firstSpot);

        // when
        spotWeatherQueryService.findWeather(secondSpot);

        // then
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
        assertThat(requestCount(RISE_SET_PATH)).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청이 실패한 결과는 캐시에 두지 않아서, 다음 조회가 기상청을 다시 불러 예보를 받는다")
    void doesNotCacheFailedForecast() {
        // given
        long spotId = insertSeoulCampsite("ACTIVE");
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH)).willReturn(serverError()));
        assertThat(spotWeatherQueryService.findWeather(spotId)).isEmpty();
        stubAllSuccess();

        // when
        Optional<SpotWeather> weather = spotWeatherQueryService.findWeather(spotId);

        // then
        assertThat(weather).isPresent();
        assertThat(weather.get().forecast().shortTerm()).isNotEmpty();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 천문연이 실패한 출몰시각은 캐시에 두지 않아서, 다음 조회가 천문연을 다시 부른다")
    void doesNotCacheFailedSunTimes() {
        // given
        long spotId = insertSeoulCampsite("ACTIVE");
        stubKmaSuccess();
        stubKasi(serverError());
        assertThat(spotWeatherQueryService.findWeather(spotId).orElseThrow().sun())
                .isNull();
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));

        // when
        SpotWeather weather = spotWeatherQueryService.findWeather(spotId).orElseThrow();

        // then
        assertThat(weather.sun()).isNotNull();
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09] 같은 발표를 캐시에서 읽어도 단기예보는 조회한 시각 이후 항목만 준다")
    void filtersCachedShortTermByRequestTime() {
        // given
        long spotId = insertSeoulCampsite("ACTIVE");
        stubAllSuccess();
        List<ShortTermForecast> before = spotWeatherQueryService
                .findWeather(spotId)
                .orElseThrow()
                .forecast()
                .shortTerm();
        // 한국 시각 14:08. 14시 발표는 14:10부터 조회하므로 서버는 아직 11시 발표를 쓴다.
        clock.setInstant(FIXTURE_TIME.plus(Duration.ofMinutes(70)));

        // when
        List<ShortTermForecast> after = spotWeatherQueryService
                .findWeather(spotId)
                .orElseThrow()
                .forecast()
                .shortTerm();

        // then
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
        assertThat(after).hasSizeLessThan(before.size());
        assertThat(after).allSatisfy(forecast -> assertThat(forecast.at()).isAfterOrEqualTo(clock.instant()));
    }

    @Test
    @DisplayName("[F-09] 새 단기예보 발표를 조회할 수 있게 되면 서버는 캐시를 쓰지 않고 기상청을 다시 부른다")
    void callsKmaAgainWhenNextShortTermPublishIsAvailable() {
        // given
        long spotId = insertSeoulCampsite("ACTIVE");
        stubAllSuccess();
        spotWeatherQueryService.findWeather(spotId);
        // 한국 시각 14:10. 14시 발표를 조회할 수 있게 된다.
        clock.setInstant(FIXTURE_TIME.plus(Duration.ofMinutes(72)));

        // when
        spotWeatherQueryService.findWeather(spotId);

        // then
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(2);
        wireMock.verify(getRequestedFor(urlPathEqualTo(VILAGE_FCST_PATH)).withQueryParam("base_time", equalTo("1400")));
    }

    private long insertSeoulCampsite(String status) {
        return insertCampsite(status, "POINT(37.5665 126.978)");
    }

    // 좌표는 SRID 4326 WKT라서 (위도 경도) 순서다. 격자는 두 장소 모두 서울시청 격자(60, 127)로 둔다.
    private long insertCampsite(String status, String wkt) {
        clock.setInstant(FIXTURE_TIME);
        String name = TestSequence.unique("야영장");
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_GeomFromText(?, 4326), 60, 127, ?, NOW(6), NOW(6))",
                name,
                wkt,
                status);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private void stubAllSuccess() {
        stubKmaSuccess();
        stubKasi(okXml(readFixture("kasi/rise-set.xml")));
    }

    private void stubKmaSuccess() {
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH)).willReturn(okJson(readFixture("kma/vilage-fcst.json"))));
        wireMock.stubFor(
                get(urlPathEqualTo(MID_LAND_FCST_PATH)).willReturn(okJson(readFixture("kma/mid-land-fcst.json"))));
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH)).willReturn(okJson(readFixture("kma/mid-ta.json"))));
    }

    private void stubKasi(ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(RISE_SET_PATH)).willReturn(response));
    }

    private int requestCount(String path) {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo(path))).size();
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
}
