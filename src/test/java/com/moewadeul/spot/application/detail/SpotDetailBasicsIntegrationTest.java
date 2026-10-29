package com.moewadeul.spot.application.detail;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LAT;
import static com.moewadeul.spot.application.detail.SpotDetailFixture.RIDGE_LNG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.SpotFixture;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.spot.application.ParkWarningTexts;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.domain.SpotType;
import com.moewadeul.spot.domain.bakji.BakjiSignalLevel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotDetailBasicsIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";

    private static final int EXTERNAL_DELAY_MILLIS = 3_000;

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private WireMockServer wireMock;

    private SpotDetailFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new SpotDetailFixture(jdbcTemplate);
    }

    @Test
    @DisplayName("[F-05][SF-06] 박지 상세는 박지 정보, 제보자 닉네임, 확인 수를 채우고 공공데이터 상세는 비우며, 경고 박지라 확인 안내는 없다")
    void findsBakjiDetail() {
        // given
        long reporterId = fixture.insertMember("새벽능선");
        long spotId = spotFixture.insert(
                aSpot().name("능선 끝 평지").address("강원특별자치도 어딘가").parkWarning(true).at(RIDGE_LAT, RIDGE_LNG));
        jdbcTemplate.update(
                "INSERT INTO bakji_detail"
                        + " (spot_id, reporter_id, description, has_water, has_toilet, signal_level, created_at, updated_at)"
                        + " VALUES (?, ?, '바람이 덜한 안쪽 평지', TRUE, FALSE, 'WEAK', NOW(6), NOW(6))",
                spotId,
                reporterId);
        insertConfirmation(spotId, fixture.insertMember(TestSequence.nickname()));
        insertConfirmation(spotId, fixture.insertMember(TestSequence.nickname()));

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.spotId()).isEqualTo(spotId);
        assertThat(detail.type()).isEqualTo(SpotType.BAKJI);
        assertThat(detail.name()).isEqualTo("능선 끝 평지");
        assertThat(detail.lat()).isCloseTo(RIDGE_LAT, within(1e-9));
        assertThat(detail.lng()).isCloseTo(RIDGE_LNG, within(1e-9));
        assertThat(detail.address()).isEqualTo("강원특별자치도 어딘가");
        assertThat(detail.parkWarning().warned()).isTrue();
        assertThat(detail.publicDetail()).isNull();
        assertThat(detail.bakji())
                .isEqualTo(new SpotBakjiDetail(
                        "바람이 덜한 안쪽 평지", true, false, BakjiSignalLevel.WEAK, 2, reporterId, "새벽능선", null));
    }

    @Test
    @DisplayName("[F-05][SF-06] 확인이 없는 박지는 확인 수가 0이고, 경고가 아니라 관할 기관 확인 안내를 담는다")
    void reportsZeroConfirmationsForUnconfirmedBakji() {
        // given
        long reporterId = fixture.insertMember(TestSequence.nickname());
        long spotId = spotFixture.insert(aSpot().at(RIDGE_LAT, RIDGE_LNG));
        jdbcTemplate.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, FALSE, TRUE, NOW(6), NOW(6))",
                spotId,
                reporterId);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.address()).isNull();
        assertThat(detail.parkWarning().warned()).isFalse();
        assertThat(detail.bakji().confirmationCount()).isZero();
        assertThat(detail.bakji().description()).isNull();
        assertThat(detail.bakji().signalLevel()).isNull();
        assertThat(detail.bakji().hasToilet()).isTrue();
        assertThat(detail.bakji().notice()).isEqualTo(ParkWarningTexts.BAKJI_CHECK_NOTICE);
        assertThat(detail.rating()).isEqualTo(new SpotRating(null, 0));
        assertThat(detail.recentReviews()).isEmpty();
    }

    @Test
    @DisplayName("[F-05] 야영장 상세는 원천 시설 항목을 응답 필드 이름으로 옮기고, 원천에 없는 항목은 null로 둔다")
    void findsCampsiteDetailWithMappedFacilities() {
        // given
        long spotId = spotFixture.insert(
                aSpot().type("CAMPSITE").name("솔숲 야영장").address("강원특별자치도 평창군").at(RIDGE_LAT, RIDGE_LNG));
        jdbcTemplate.update(
                "INSERT INTO public_spot_detail"
                        + " (spot_id, source, external_id, category, facilities, phone, homepage, operating_status,"
                        + " synced_at, created_at, updated_at)"
                        + " VALUES (?, 'GOCAMPING', ?, '일반야영장,자동차야영장', ?, '033-123-4567',"
                        + " 'https://example.com', 'OPERATING', NOW(6), NOW(6), NOW(6))",
                spotId,
                String.valueOf(spotId),
                "{\"toiletCo\":\"3\",\"wtrplCo\":\"2\",\"brazierCl\":\"개별\",\"sbrsCl\":\"전기,온수\","
                        + "\"sbrsEtc\":\"매점\",\"posblFcltyCl\":\"계곡 물놀이\",\"animalCmgCl\":\"불가능\"}");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.type()).isEqualTo(SpotType.CAMPSITE);
        assertThat(detail.lat()).isCloseTo(RIDGE_LAT, within(1e-9));
        assertThat(detail.lng()).isCloseTo(RIDGE_LNG, within(1e-9));
        assertThat(detail.bakji()).isNull();
        assertThat(detail.publicDetail())
                .isEqualTo(new SpotPublicDetail(
                        PublicSpotSource.GOCAMPING,
                        "일반야영장,자동차야영장",
                        new SpotFacilities("3", null, "2", "개별", "전기,온수", "매점", "계곡 물놀이", null, "불가능"),
                        "033-123-4567",
                        "https://example.com",
                        null,
                        null,
                        PublicSpotOperatingStatus.OPERATING,
                        null,
                        null,
                        false));
    }

    @Test
    @DisplayName("[F-05] 시설 JSON이 빈 객체이면 시설 정보를 null로 준다")
    void returnsNullFacilitiesForEmptyJsonObject() {
        // given
        long spotId = spotFixture.insert(aSpot().type("CAMPSITE").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(spotId, "GOCAMPING", "{}", null, null, null, null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail().facilities()).isNull();
        assertThat(detail.publicDetail().operatingStatus()).isNull();
    }

    @Test
    @DisplayName("[F-05] 자연휴양림 상세는 시설 정보가 없으면 null로 주고, 원천 기준일을 함께 준다")
    void findsForestDetailWithoutFacilities() {
        // given
        long spotId = spotFixture.insert(
                aSpot().type("FOREST").name("숲속 휴양림").address("충청북도 어딘가").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(spotId, "FOREST", null, LocalDate.parse("2025-12-31"), null, null, null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.type()).isEqualTo(SpotType.FOREST);
        assertThat(detail.bakji()).isNull();
        assertThat(detail.publicDetail())
                .isEqualTo(new SpotPublicDetail(
                        PublicSpotSource.FOREST,
                        null,
                        null,
                        null,
                        null,
                        LocalDate.parse("2025-12-31"),
                        null,
                        null,
                        null,
                        null,
                        false));
    }

    @ParameterizedTest
    @CsvSource({"CAMPSITE, HIDDEN", "CAMPSITE, DELETED", "BAKJI, PENDING_REVIEW", "BAKJI, HIDDEN", "BAKJI, DELETED"})
    @DisplayName("[F-05] 숨김, 삭제, 검토 대기 상태의 장소는 NOT_FOUND로 거부한다")
    void rejectsSpotsThatAreNotActive(String type, String status) {
        // given
        long spotId = spotFixture.insert(aSpot().type(type).status(status).at(RIDGE_LAT, RIDGE_LNG));
        if (type.equals("BAKJI")) {
            jdbcTemplate.update(
                    "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                            + " VALUES (?, ?, FALSE, FALSE, NOW(6), NOW(6))",
                    spotId,
                    fixture.insertMember(TestSequence.nickname()));
        } else {
            fixture.insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);
        }

        // when & then
        assertThatThrownBy(() -> spotDetailQueryService.findDetail(spotId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("[F-05] 없는 장소 ID는 NOT_FOUND로 거부한다")
    void rejectsUnknownSpot() {
        assertThatThrownBy(() -> spotDetailQueryService.findDetail(Long.MAX_VALUE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("[F-05][NFR-05] 상세는 기상청·천문연을 부르지 않아서, 두 API가 3초씩 늦어도 기다리지 않고 응답한다")
    void returnsDetailWithoutCallingWeatherApis() {
        // given
        long spotId = insertSeoulCampsite();
        wireMock.stubFor(get(urlPathEqualTo(VILAGE_FCST_PATH))
                .willReturn(okJson(readFixture("kma/vilage-fcst.json")).withFixedDelay(EXTERNAL_DELAY_MILLIS)));
        stubKasi(okXml(readFixture("kasi/rise-set.xml")).withFixedDelay(EXTERNAL_DELAY_MILLIS));

        // when
        long startedAt = System.nanoTime();
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        // then
        assertThat(detail.publicDetail()).isNotNull();
        assertThat(elapsedMillis).isLessThan(EXTERNAL_DELAY_MILLIS);
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    private long insertSeoulCampsite() {
        clock.setInstant(Instant.parse("2026-10-04T03:58:00Z"));
        long spotId = spotFixture.insert(aSpot().type("CAMPSITE").at(RIDGE_LAT, RIDGE_LNG));
        fixture.insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);
        return spotId;
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

    private void insertConfirmation(long spotId, long memberId) {
        jdbcTemplate.update(
                "INSERT INTO bakji_confirmation (spot_id, member_id, created_at) VALUES (?, ?, NOW(6))",
                spotId,
                memberId);
    }
}
