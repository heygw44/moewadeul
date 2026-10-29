package com.moewadeul.spot.application.detail;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.spot.application.ParkWarningTexts;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.SpotParkWarning;
import com.moewadeul.spot.application.park.ProtectedAreaSource;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.domain.SpotType;
import com.moewadeul.spot.domain.bakji.BakjiSignalLevel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotDetailQueryServiceIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";

    private static final Coordinate RIDGE = new Coordinate(37.71, 128.75);

    private static final int EXTERNAL_DELAY_MILLIS = 3_000;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private WireMockServer wireMock;

    @Test
    @DisplayName("[F-05][SF-06] 박지 상세는 박지 정보, 제보자 닉네임, 확인 수를 채우고 공공데이터 상세는 비우며, 경고 박지라 확인 안내는 없다")
    void findsBakjiDetail() {
        // given
        long reporterId = insertMember("새벽능선");
        long spotId = insertSpot("BAKJI", "ACTIVE", "능선 끝 평지", "강원특별자치도 어딘가", true, RIDGE);
        jdbcTemplate.update(
                "INSERT INTO bakji_detail"
                        + " (spot_id, reporter_id, description, has_water, has_toilet, signal_level, created_at, updated_at)"
                        + " VALUES (?, ?, '바람이 덜한 안쪽 평지', TRUE, FALSE, 'WEAK', NOW(6), NOW(6))",
                spotId,
                reporterId);
        insertConfirmation(spotId, insertMember(TestSequence.nickname()));
        insertConfirmation(spotId, insertMember(TestSequence.nickname()));

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.spotId()).isEqualTo(spotId);
        assertThat(detail.type()).isEqualTo(SpotType.BAKJI);
        assertThat(detail.name()).isEqualTo("능선 끝 평지");
        assertThat(detail.lat()).isCloseTo(RIDGE.lat(), within(1e-9));
        assertThat(detail.lng()).isCloseTo(RIDGE.lng(), within(1e-9));
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
        long reporterId = insertMember(TestSequence.nickname());
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
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
        long spotId = insertSpot("CAMPSITE", "ACTIVE", "솔숲 야영장", "강원특별자치도 평창군", false, RIDGE);
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
        assertThat(detail.lat()).isCloseTo(RIDGE.lat(), within(1e-9));
        assertThat(detail.lng()).isCloseTo(RIDGE.lng(), within(1e-9));
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
        long spotId = insertSpot("CAMPSITE", "ACTIVE", TestSequence.unique("야영장"), null, false, RIDGE);
        insertPublicDetail(spotId, "GOCAMPING", "{}", null, null, null, null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail().facilities()).isNull();
        assertThat(detail.publicDetail().operatingStatus()).isNull();
    }

    @Test
    @DisplayName("[F-05] 휴장 기간이 한국 날짜로 시작되면 휴장으로, 시작 전날이면 휴장이 아닌 것으로 판정한다")
    void judgesClosedNowByKoreanDate() {
        // given
        long spotId = insertSpot("CAMPSITE", "ACTIVE", TestSequence.unique("야영장"), null, false, RIDGE);
        insertPublicDetail(
                spotId,
                "GOCAMPING",
                null,
                null,
                "OPERATING",
                LocalDate.parse("2026-11-01"),
                LocalDate.parse("2027-03-31"));

        // when
        clock.setInstant(Instant.parse("2026-10-31T14:30:00Z"));
        SpotDetail beforeClosure = spotDetailQueryService.findDetail(spotId);
        // when
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        SpotDetail duringClosure = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(beforeClosure.publicDetail().closedNow()).isFalse();
        assertThat(duringClosure.publicDetail().closedNow()).isTrue();
        assertThat(duringClosure.publicDetail().operatingStatus()).isEqualTo(PublicSpotOperatingStatus.OPERATING);
        assertThat(duringClosure.publicDetail().closedFrom()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(duringClosure.publicDetail().closedUntil()).isEqualTo(LocalDate.parse("2027-03-31"));
    }

    @Test
    @DisplayName("[F-05] 휴장 기간이 없고 운영 상태가 휴장이면 휴장으로 판정한다")
    void judgesClosedNowByOperatingStatusWithoutPeriod() {
        // given
        long spotId = insertSpot("CAMPSITE", "ACTIVE", TestSequence.unique("야영장"), null, false, RIDGE);
        insertPublicDetail(spotId, "GOCAMPING", null, null, "TEMPORARILY_CLOSED", null, null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail().operatingStatus()).isEqualTo(PublicSpotOperatingStatus.TEMPORARILY_CLOSED);
        assertThat(detail.publicDetail().closedNow()).isTrue();
    }

    @Test
    @DisplayName("[F-05] 자연휴양림 상세는 시설 정보가 없으면 null로 주고, 원천 기준일을 함께 준다")
    void findsForestDetailWithoutFacilities() {
        // given
        long spotId = insertSpot("FOREST", "ACTIVE", "숲속 휴양림", "충청북도 어딘가", false, RIDGE);
        insertPublicDetail(spotId, "FOREST", null, LocalDate.parse("2025-12-31"), null, null, null);

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

    @Test
    @DisplayName("[F-05] 점검 시각은 장소 원천과 같은 종류에서 마지막으로 성공한 동기화의 종료 시각이고, 실패와 실행 중인 기록은 무시한다")
    void reportsLatestCompletedSyncFinishTimeOfSameSource() {
        // given
        long spotId = insertSpot("CAMPSITE", "ACTIVE", TestSequence.unique("야영장"), null, false, RIDGE);
        insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);
        insertSyncRun("GOCAMPING", "COMPLETED", "2026-10-01 03:00:00.000000", "2026-10-01 03:10:00.000000");
        insertSyncRun("GOCAMPING", "COMPLETED", "2026-10-02 03:00:00.000000", "2026-10-02 03:12:30.500000");
        insertSyncRun("GOCAMPING", "FAILED", "2026-10-03 03:00:00.000000", "2026-10-03 03:05:00.000000");
        insertSyncRun("GOCAMPING", "RUNNING", "2026-10-04 03:00:00.000000", null);
        insertSyncRun("FOREST", "COMPLETED", "2026-10-05 03:00:00.000000", "2026-10-05 03:01:00.000000");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail().checkedAt()).isEqualTo(Instant.parse("2026-10-02T03:12:30.500Z"));
    }

    @Test
    @DisplayName("[F-05] 성공한 동기화 기록이 없거나 다른 종류뿐이면 점검 시각은 null이다")
    void returnsNullCheckedAtWithoutCompletedRunOfSameSource() {
        // given
        long spotId = insertSpot("CAMPSITE", "ACTIVE", TestSequence.unique("야영장"), null, false, RIDGE);
        insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);

        // when, then
        assertThat(spotDetailQueryService.findDetail(spotId).publicDetail().checkedAt())
                .isNull();

        // when, then
        insertSyncRun("FOREST", "COMPLETED", "2026-10-05 03:00:00.000000", "2026-10-05 03:01:00.000000");
        insertSyncRun("GOCAMPING", "FAILED", "2026-10-06 03:00:00.000000", "2026-10-06 03:01:00.000000");
        assertThat(spotDetailQueryService.findDetail(spotId).publicDetail().checkedAt())
                .isNull();
    }

    @Test
    @DisplayName("[F-05] 자연휴양림 상세의 점검 시각은 자연휴양림 동기화 기록을 따른다")
    void forestDetailUsesForestSyncRuns() {
        // given
        long spotId = insertSpot("FOREST", "ACTIVE", TestSequence.unique("휴양림"), null, false, RIDGE);
        insertPublicDetail(spotId, "FOREST", null, LocalDate.parse("2025-12-31"), null, null, null);
        insertSyncRun("GOCAMPING", "COMPLETED", "2026-10-07 03:00:00.000000", "2026-10-07 03:30:00.000000");
        insertSyncRun("FOREST", "COMPLETED", "2026-10-05 03:00:00.000000", "2026-10-05 03:01:00.000000");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail().checkedAt()).isEqualTo(Instant.parse("2026-10-05T03:01:00Z"));
    }

    @Test
    @DisplayName("[F-05] 박지 상세에는 공공데이터 상세가 없으므로 동기화 기록이 있어도 점검 시각을 주지 않는다")
    void bakjiHasNoCheckedAt() {
        // given
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        jdbcTemplate.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, FALSE, FALSE, NOW(6), NOW(6))",
                spotId,
                insertMember(TestSequence.nickname()));
        insertSyncRun("GOCAMPING", "COMPLETED", "2026-10-07 03:00:00.000000", "2026-10-07 03:30:00.000000");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.publicDetail()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"CAMPSITE, HIDDEN", "CAMPSITE, DELETED", "BAKJI, PENDING_REVIEW", "BAKJI, HIDDEN", "BAKJI, DELETED"})
    @DisplayName("[F-05] 숨김, 삭제, 검토 대기 상태의 장소는 NOT_FOUND로 거부한다")
    void rejectsSpotsThatAreNotActive(String type, String status) {
        // given
        long spotId = insertSpot(type, status, TestSequence.unique(status), null, false, RIDGE);
        if (type.equals("BAKJI")) {
            jdbcTemplate.update(
                    "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                            + " VALUES (?, ?, FALSE, FALSE, NOW(6), NOW(6))",
                    spotId,
                    insertMember(TestSequence.nickname()));
        } else {
            insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);
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
    @DisplayName("[F-05] 경계 행이 있는 경고 박지는 공원 이름, 출처, 기준일, 참고용 고지, 안내를 채운다")
    void fillsParkWarningWithAreaForWarnedBakji() {
        // given
        long areaId = insertProtectedArea("설악산 국립공원");
        long spotId = insertWarnedBakji(areaId);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.parkWarning())
                .isEqualTo(new SpotParkWarning(
                        true,
                        "설악산 국립공원",
                        ProtectedAreaSource.KDPA,
                        LocalDate.parse("2026-01-01"),
                        "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.",
                        "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요."));
    }

    @Test
    @DisplayName("[F-05] 경계 행이 없는 경고 박지는 공원 이름, 출처, 기준일만 비우고 고지와 안내는 채운다")
    void fillsNoticeAndGuideForWarnedBakjiWithoutArea() {
        // given
        long spotId = insertWarnedBakji(null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.parkWarning())
                .isEqualTo(new SpotParkWarning(
                        true,
                        null,
                        null,
                        null,
                        "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.",
                        "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요."));
    }

    @Test
    @DisplayName("[F-05] 경고가 아닌 장소는 warned만 false이고 나머지 경고 필드는 null이다")
    void leavesOtherParkWarningFieldsNullWhenNotWarned() {
        // given
        long spotId = insertSpot("CAMPSITE", "ACTIVE", TestSequence.unique("야영장"), null, false, RIDGE);
        insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.parkWarning()).isEqualTo(new SpotParkWarning(false, null, null, null, null, null));
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

    @Test
    @DisplayName("[F-05] 겹치는 확정 베이스캠프는 밤마다 인원을 합산하고, 종료일은 밤으로 세지 않는다")
    void sumsConfirmedMembersPerNightAndExcludesEndDate() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long first = insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-09");
        insertBasecampMembers(first, "ACTIVE", 3);
        long second = insertBasecamp(spotId, "CONFIRMED", "2026-11-08", "2026-11-09");
        insertBasecampMembers(second, "ACTIVE", 2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople())
                .containsExactly(
                        new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 3),
                        new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 5));
    }

    @Test
    @DisplayName("[F-05] 탈퇴하거나 강퇴된 멤버는 예상 인원에 세지 않는다")
    void doesNotCountLeftOrKickedMembers() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long basecampId = insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-08");
        insertBasecampMembers(basecampId, "ACTIVE", 2);
        insertBasecampMembers(basecampId, "LEFT", 1);
        insertBasecampMembers(basecampId, "KICKED", 1);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).containsExactly(new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 2));
    }

    @Test
    @DisplayName("[F-05] 확정이 아닌 베이스캠프와 다른 장소의 베이스캠프는 예상 인원에 세지 않는다")
    void countsOnlyConfirmedBasecampsOfThisSpot() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long otherSpotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        for (String status : new String[] {"RECRUITING", "CLOSED", "CANCELED", "COMPLETED"}) {
            insertBasecampMembers(insertBasecamp(spotId, status, "2026-11-07", "2026-11-08"), "ACTIVE", 2);
        }
        insertBasecampMembers(insertBasecamp(otherSpotId, "CONFIRMED", "2026-11-07", "2026-11-08"), "ACTIVE", 2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).isEmpty();
    }

    @Test
    @DisplayName("[F-05] 이미 지난 밤은 예상 인원에서 빼고, 오늘 밤부터 센다")
    void excludesPastNights() {
        // given
        clock.setInstant(Instant.parse("2026-11-08T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        insertBasecampMembers(insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-10"), "ACTIVE", 2);
        insertBasecampMembers(insertBasecamp(spotId, "CONFIRMED", "2026-11-06", "2026-11-08"), "ACTIVE", 4);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople())
                .containsExactly(
                        new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 2),
                        new SpotExpectedPeople(LocalDate.parse("2026-11-09"), 2));
    }

    @Test
    @DisplayName("[F-23][ST-05] 개인 일정은 밤마다 1명으로 예상 인원에 더하고, 같은 밤의 확정 베이스캠프 인원과 합산한다")
    void addsSoloTripsToExpectedPeople() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        insertSoloTrip(insertMember(TestSequence.nickname()), spotId, "2026-11-07", "2026-11-09");
        insertBasecampMembers(insertBasecamp(spotId, "CONFIRMED", "2026-11-08", "2026-11-09"), "ACTIVE", 2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople())
                .containsExactly(
                        new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 1),
                        new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 3));
    }

    @Test
    @DisplayName("[F-23][ST-05] 탈퇴한 회원의 개인 일정, 다른 장소의 일정, 이미 끝난 일정은 예상 인원에 세지 않는다")
    void excludesWithdrawnOtherSpotAndFinishedSoloTrips() {
        // given
        clock.setInstant(Instant.parse("2026-11-08T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long otherSpotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long withdrawnId = insertMember(TestSequence.nickname());
        jdbcTemplate.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", withdrawnId);
        insertSoloTrip(withdrawnId, spotId, "2026-11-09", "2026-11-10");
        insertSoloTrip(insertMember(TestSequence.nickname()), otherSpotId, "2026-11-09", "2026-11-10");
        insertSoloTrip(insertMember(TestSequence.nickname()), spotId, "2026-11-06", "2026-11-08");
        insertSoloTrip(insertMember(TestSequence.nickname()), spotId, "2026-11-07", "2026-11-09");

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).containsExactly(new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 1));
    }

    @Test
    @DisplayName("[F-05] 예상 인원의 오늘은 UTC가 아니라 한국 날짜로 정한다")
    void decidesTodayByKoreanDate() {
        // given
        clock.setInstant(Instant.parse("2026-11-07T15:30:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        insertBasecampMembers(insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-09"), "ACTIVE", 2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).containsExactly(new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 2));
    }

    @Test
    @DisplayName("[F-05] 숙박 행사의 확정 참가자를 예상 인원에 더하고, 같은 밤의 확정 베이스캠프 인원과 합산한다")
    void addsConfirmedProgramParticipantsToExpectedPeople() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long programId = insertProgram(spotId, "2026-11-07T01:00:00Z", "2026-11-08T03:00:00Z", true, "SCHEDULED");
        insertProgramApplications(programId, "CONFIRMED", 2);
        insertProgramApplications(programId, "PENDING_PAYMENT", 1);
        insertProgramApplications(programId, "CANCELED", 1);
        insertProgramApplications(programId, "EXPIRED", 1);
        insertBasecampMembers(insertBasecamp(spotId, "CONFIRMED", "2026-11-07", "2026-11-09"), "ACTIVE", 3);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople())
                .containsExactly(
                        new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 5),
                        new SpotExpectedPeople(LocalDate.parse("2026-11-08"), 3));
    }

    @Test
    @DisplayName("[F-05] 행사의 야영 밤은 시작·종료 시각의 UTC 날짜가 아니라 한국 날짜로 정한다")
    void countsProgramNightsByKoreanDate() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long programId = insertProgram(spotId, "2026-11-06T16:00:00Z", "2026-11-07T16:00:00Z", true, "SCHEDULED");
        insertProgramApplications(programId, "CONFIRMED", 4);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).containsExactly(new SpotExpectedPeople(LocalDate.parse("2026-11-07"), 4));
    }

    @Test
    @DisplayName("[F-05] 당일 행사, 취소된 행사, 다른 장소의 행사, 끝난 행사의 참가자는 예상 인원에 세지 않는다")
    void excludesDayCanceledOtherSpotAndPastPrograms() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long otherSpotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        String start = "2026-11-07T01:00:00Z";
        String end = "2026-11-08T03:00:00Z";
        insertProgramApplications(insertProgram(spotId, start, end, false, "SCHEDULED"), "CONFIRMED", 2);
        insertProgramApplications(insertProgram(spotId, start, end, true, "CANCELED"), "CONFIRMED", 2);
        insertProgramApplications(insertProgram(otherSpotId, start, end, true, "SCHEDULED"), "CONFIRMED", 2);
        insertProgramApplications(
                insertProgram(spotId, "2026-10-30T01:00:00Z", "2026-10-31T03:00:00Z", true, "SCHEDULED"),
                "CONFIRMED",
                2);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).isEmpty();
    }

    @Test
    @DisplayName("[F-05] 모집 중인 베이스캠프만 출발일, ID 순서로 인원과 합류 조건을 채워 준다")
    void listsOnlyRecruitingBasecampsInOrder() {
        // given
        clock.setInstant(Instant.parse("2026-11-01T03:00:00Z"));
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long otherSpotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);
        long later = insertBasecamp(spotId, "RECRUITING", "2026-11-14", "2026-11-16", 4, "늦은 출발");
        long sameDayFirst = insertBasecamp(spotId, "RECRUITING", "2026-11-07", "2026-11-08", 6, "같은 날 먼저");
        long sameDaySecond = insertBasecamp(spotId, "RECRUITING", "2026-11-07", "2026-11-09", 3, "같은 날 나중");
        insertBasecamp(spotId, "CLOSED", "2026-11-05", "2026-11-06");
        insertBasecamp(spotId, "CONFIRMED", "2026-11-05", "2026-11-06");
        insertBasecamp(otherSpotId, "RECRUITING", "2026-11-05", "2026-11-06");
        insertBasecampMembers(sameDayFirst, "ACTIVE", 3);
        insertBasecampMembers(sameDayFirst, "LEFT", 1);
        insertBasecampMembers(later, "ACTIVE", 1);
        jdbcTemplate.update(
                "UPDATE basecamp SET min_trust_level = 1, age_group_min = 20, age_group_max = 30,"
                        + " same_gender_only = TRUE, required_gender = 'FEMALE' WHERE id = ?",
                sameDayFirst);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.recruitingBasecamps())
                .containsExactly(
                        new SpotRecruitingBasecamp(
                                sameDayFirst,
                                "같은 날 먼저",
                                LocalDate.parse("2026-11-07"),
                                LocalDate.parse("2026-11-08"),
                                6,
                                3,
                                new SpotJoinCondition(1, 20, 30, true)),
                        new SpotRecruitingBasecamp(
                                sameDaySecond,
                                "같은 날 나중",
                                LocalDate.parse("2026-11-07"),
                                LocalDate.parse("2026-11-09"),
                                3,
                                0,
                                new SpotJoinCondition(null, null, null, false)),
                        new SpotRecruitingBasecamp(
                                later,
                                "늦은 출발",
                                LocalDate.parse("2026-11-14"),
                                LocalDate.parse("2026-11-16"),
                                4,
                                1,
                                new SpotJoinCondition(null, null, null, false)));
    }

    @Test
    @DisplayName("[F-05] 모집 중인 베이스캠프와 확정 베이스캠프가 없으면 두 목록은 빈 목록이다")
    void returnsEmptyListsWithoutBasecamps() {
        // given
        long spotId = insertSpot("BAKJI", "ACTIVE", TestSequence.unique("박지"), null, false, RIDGE);

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.expectedPeople()).isEmpty();
        assertThat(detail.recruitingBasecamps()).isEmpty();
    }

    private long insertSeoulCampsite() {
        clock.setInstant(Instant.parse("2026-10-04T03:58:00Z"));
        long spotId = insertSpot("CAMPSITE", "ACTIVE", TestSequence.unique("야영장"), null, false, RIDGE);
        insertPublicDetail(spotId, "GOCAMPING", null, null, "OPERATING", null, null);
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

    private long insertProtectedArea(String name) {
        jdbcTemplate.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES (?, 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText('MULTIPOLYGON(((128 37, 129 37, 129 38, 128 38, 128 37)))', 4326,"
                        + " 'axis-order=long-lat'), NOW(6), NOW(6))",
                name);
        return jdbcTemplate.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
    }

    private long insertWarnedBakji(Long areaId) {
        String name = TestSequence.unique("경고박지");
        long spotId = insertSpot("BAKJI", "ACTIVE", name, null, true, RIDGE);
        jdbcTemplate.update("UPDATE spot SET protected_area_id = ? WHERE id = ?", areaId, spotId);
        jdbcTemplate.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, FALSE, FALSE, NOW(6), NOW(6))",
                spotId,
                insertMember(TestSequence.nickname()));
        return spotId;
    }

    private long insertSpot(
            String type, String status, String name, String address, boolean parkWarning, Coordinate at) {
        jdbcTemplate.update(
                "INSERT INTO spot"
                        + " (type, name, location, address, weather_nx, weather_ny, park_warning, status,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), ?, 60, 127, ?, ?, NOW(6), NOW(6))",
                type,
                name,
                at.wkt(),
                address,
                parkWarning,
                status);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private void insertPublicDetail(
            long spotId,
            String source,
            String facilities,
            LocalDate sourceDate,
            String operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil) {
        jdbcTemplate.update(
                "INSERT INTO public_spot_detail"
                        + " (spot_id, source, external_id, facilities, source_date, operating_status, closed_from,"
                        + " closed_until, synced_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))",
                spotId,
                source,
                String.valueOf(spotId),
                facilities,
                sourceDate,
                operatingStatus,
                closedFrom,
                closedUntil);
    }

    private void insertSyncRun(String jobType, String status, String startedAt, String finishedAt) {
        jdbcTemplate.update(
                "INSERT INTO sync_job_run (job_type, status, processed_count, skipped_count, started_at,"
                        + " finished_at, created_at, updated_at)"
                        + " VALUES (?, ?, 0, 0, ?, ?, NOW(6), NOW(6))",
                jobType,
                status,
                startedAt,
                finishedAt);
    }

    private void insertConfirmation(long spotId, long memberId) {
        jdbcTemplate.update(
                "INSERT INTO bakji_confirmation (spot_id, member_id, created_at) VALUES (?, ?, NOW(6))",
                spotId,
                memberId);
    }

    private long insertBasecamp(long spotId, String status, String startDate, String endDate) {
        return insertBasecamp(spotId, status, startDate, endDate, 6, TestSequence.unique("베이스캠프"));
    }

    private long insertBasecamp(
            long spotId, String status, String startDate, String endDate, int capacity, String title) {
        long leaderId = insertMember(TestSequence.nickname());
        jdbcTemplate.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, ?, '설명', ?, ?, ?, ?, NOW(6), NOW(6))",
                leaderId,
                spotId,
                title,
                startDate,
                endDate,
                capacity,
                status);
        return jdbcTemplate.queryForObject("SELECT id FROM basecamp WHERE title = ?", Long.class, title);
    }

    private void insertBasecampMembers(long basecampId, String status, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at,"
                            + " updated_at) VALUES (?, ?, 'MEMBER', ?, NOW(6), NOW(6), NOW(6))",
                    basecampId,
                    insertMember(TestSequence.nickname()),
                    status);
        }
    }

    private void insertSoloTrip(long memberId, long spotId, String startDate, String endDate) {
        jdbcTemplate.update(
                "INSERT INTO solo_trip (member_id, spot_id, start_date, end_date, created_at)"
                        + " VALUES (?, ?, ?, ?, NOW(6))",
                memberId,
                spotId,
                startDate,
                endDate);
    }

    private long insertProgram(long spotId, String startAt, String endAt, boolean overnight, String status) {
        String title = TestSequence.unique("행사");
        jdbcTemplate.update(
                "INSERT INTO program (title, description, spot_id, location_text, start_at, end_at, capacity, fee,"
                        + " apply_open_at, apply_close_at, payment_deadline_minutes, overnight, status, created_by,"
                        + " created_at, updated_at) VALUES (?, '설명', ?, '입구', ?, ?, 20, 30000, '2026-10-01 00:00:00',"
                        + " '2026-10-20 00:00:00', 15, ?, ?, ?, NOW(6), NOW(6))",
                title,
                spotId,
                toUtcDateTime(startAt),
                toUtcDateTime(endAt),
                overnight,
                status,
                insertMember(TestSequence.nickname()));
        return jdbcTemplate.queryForObject("SELECT id FROM program WHERE title = ?", Long.class, title);
    }

    private void insertProgramApplications(long programId, String status, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO program_application (program_id, member_id, status, payment_due_at, created_at,"
                            + " updated_at) VALUES (?, ?, ?, '2026-10-31 00:00:00', NOW(6), NOW(6))",
                    programId,
                    insertMember(TestSequence.nickname()),
                    status);
        }
    }

    private static String toUtcDateTime(String isoInstant) {
        return Instant.parse(isoInstant).toString().replace("T", " ").replace("Z", "");
    }

    private long insertMember(String nickname) {
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'USER', NOW(6), NOW(6))",
                TestSequence.email(),
                nickname);
        return jdbcTemplate.queryForObject("SELECT id FROM member WHERE nickname = ?", Long.class, nickname);
    }

    private record Coordinate(double lat, double lng) {

        // SRID 4326 좌표의 WKT는 위도, 경도 순서다.
        String wkt() {
            return String.format(Locale.ROOT, "POINT(%s %s)", lat, lng);
        }
    }
}
