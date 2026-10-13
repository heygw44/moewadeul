package com.moewadeul.spot.api.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.SpotParkWarning;
import com.moewadeul.spot.application.detail.SpotBakjiDetail;
import com.moewadeul.spot.application.detail.SpotDetail;
import com.moewadeul.spot.application.detail.SpotDetailQueryService;
import com.moewadeul.spot.application.detail.SpotExpectedPeople;
import com.moewadeul.spot.application.detail.SpotFacilities;
import com.moewadeul.spot.application.detail.SpotJoinCondition;
import com.moewadeul.spot.application.detail.SpotPublicDetail;
import com.moewadeul.spot.application.detail.SpotRating;
import com.moewadeul.spot.application.detail.SpotRecentReview;
import com.moewadeul.spot.application.detail.SpotRecruitingBasecamp;
import com.moewadeul.spot.application.detail.SpotWeather;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.domain.SpotType;
import com.moewadeul.weather.application.SunTimes;
import com.moewadeul.weather.application.WeatherForecast;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(SpotDetailController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class SpotDetailControllerTest {

    private static final String PATH = "/api/spots";

    private static final SpotParkWarning WARNED = new SpotParkWarning(
            true,
            "설악산 국립공원",
            "KDPA",
            LocalDate.of(2026, 1, 1),
            "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.",
            "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요.");
    private static final SpotRating NO_RATING = new SpotRating(null, 0);
    private static final SpotParkWarning NOT_WARNED = new SpotParkWarning(false, null, null, null, null, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SpotDetailQueryService spotDetailQueryService;

    @Test
    @DisplayName("[F-05] 로그인하지 않은 사용자가 박지 상세를 조회하면 박지 상세와 제보자를 받고, publicDetail은 null이며 아직 채우지 않는 항목은 빈 값이다")
    void anonymousReadsBakjiDetail() {
        // given
        SpotBakjiDetail bakji = new SpotBakjiDetail("능선 끝 평지", false, true, "WEAK", 12L, 31L, "새벽능선");
        when(spotDetailQueryService.findDetail(101L))
                .thenReturn(new SpotDetail(
                        101L,
                        SpotType.BAKJI,
                        "능선 끝 평지",
                        37.71,
                        128.75,
                        "강원 평창군",
                        WARNED,
                        bakji,
                        null,
                        NO_RATING,
                        List.of(),
                        List.of(),
                        List.of(),
                        null));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/101").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "spotId": 101, "type": "BAKJI", "name": "능선 끝 평지", "lat": 37.71, "lng": 128.75,
                  "address": "강원 평창군",
                  "bakji": {
                    "description": "능선 끝 평지", "hasWater": false, "hasToilet": true, "signalLevel": "WEAK",
                    "confirmationCount": 12, "reporter": { "memberId": 31, "nickname": "새벽능선" }
                  },
                  "publicDetail": null,
                  "parkWarning": {
                    "warned": true, "areaName": "설악산 국립공원", "source": "KDPA", "sourceDate": "2026-01-01",
                    "notice": "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.",
                    "guide": "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요."
                  },
                  "rating": { "average": null, "count": 0 },
                  "recentReviews": [], "expectedPeople": [], "recruitingBasecamps": [], "weather": null
                }
                """);
    }

    @Test
    @DisplayName("[F-10] 후기가 있는 장소의 상세는 평균 평점, 후기 수, 최근 후기를 후기 목록 항목과 같은 모양으로 응답한다")
    void detailShowsRatingAndRecentReviews() {
        // given
        SpotRecentReview review = new SpotRecentReview(
                9L,
                31L,
                "새벽능선",
                LocalDate.of(2026, 10, 4),
                5,
                "별이 좋았다",
                List.of(new SpotRecentReview.Image(5L, "https://view.test.invalid/review/31/a.jpg")),
                Instant.parse("2026-10-05T03:00:00Z"));
        when(spotDetailQueryService.findDetail(106L))
                .thenReturn(new SpotDetail(
                        106L,
                        SpotType.CAMPSITE,
                        "숲속 야영장",
                        37.52,
                        127.81,
                        null,
                        NOT_WARNED,
                        null,
                        null,
                        new SpotRating(4.3, 3),
                        List.of(review),
                        List.of(),
                        List.of(),
                        null));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/106").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.rating").isEqualTo(Map.of("average", 4.3, "count", 3));
        assertThat(result).bodyJson().extractingPath("$.recentReviews").asList().hasSize(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.recentReviews[0]")
                .isEqualTo(Map.of(
                        "reviewId",
                        9,
                        "author",
                        Map.of("memberId", 31, "nickname", "새벽능선"),
                        "visitedDate",
                        "2026-10-04",
                        "rating",
                        5,
                        "content",
                        "별이 좋았다",
                        "images",
                        List.of(Map.of("imageId", 5, "url", "https://view.test.invalid/review/31/a.jpg")),
                        "createdAt",
                        "2026-10-05T03:00:00Z"));
    }

    @Test
    @DisplayName("[F-05] 야영장 상세를 조회하면 원천 정보와 시설, 휴장 여부를 받고, bakji는 null이다")
    void readsCampsiteDetail() {
        // given
        SpotFacilities facilities = new SpotFacilities("4", "2", "3", "개별", "전기,온수", null, "산책로", "계곡", "불가능");
        SpotPublicDetail publicDetail = new SpotPublicDetail(
                PublicSpotSource.GOCAMPING,
                "일반야영장,자동차야영장",
                facilities,
                "033-000-0000",
                "https://camp.example.com",
                LocalDate.of(2025, 12, 31),
                Instant.parse("2026-10-02T03:12:30Z"),
                PublicSpotOperatingStatus.OPERATING,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                true);
        when(spotDetailQueryService.findDetail(102L))
                .thenReturn(new SpotDetail(
                        102L,
                        SpotType.CAMPSITE,
                        "숲속 야영장",
                        37.52,
                        127.81,
                        null,
                        NOT_WARNED,
                        null,
                        publicDetail,
                        NO_RATING,
                        List.of(),
                        List.of(),
                        List.of(),
                        null));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/102").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "spotId": 102, "type": "CAMPSITE", "name": "숲속 야영장", "lat": 37.52, "lng": 127.81,
                  "address": null,
                  "bakji": null,
                  "publicDetail": {
                    "source": "GOCAMPING", "category": "일반야영장,자동차야영장",
                    "facilities": {
                      "toiletCount": "4", "showerCount": "2", "sinkCount": "3", "brazier": "개별",
                      "amenities": "전기,온수", "amenitiesEtc": null, "nearbyFacilities": "산책로",
                      "nearbyFacilitiesEtc": "계곡", "petPolicy": "불가능"
                    },
                    "phone": "033-000-0000", "homepage": "https://camp.example.com", "sourceDate": "2025-12-31",
                    "checkedAt": "2026-10-02T03:12:30Z",
                    "operatingStatus": "OPERATING", "closedFrom": "2026-10-01", "closedUntil": "2026-10-31",
                    "closedNow": true
                  },
                  "parkWarning": { "warned": false },
                  "rating": { "average": null, "count": 0 },
                  "recentReviews": [], "expectedPeople": [], "recruitingBasecamps": [], "weather": null
                }
                """);
    }

    @Test
    @DisplayName("[F-05][F-09][SF-05] 경고 박지에 날씨가 있으면 경고 필드 전체와 단기·중기 예보, 출몰시각을 응답한다")
    void readsWarnedBakjiDetailWithWeather() {
        // given
        SpotBakjiDetail bakji = new SpotBakjiDetail("능선 끝 평지", false, true, "WEAK", 12L, 31L, "새벽능선");
        WeatherForecast forecast = new WeatherForecast(
                "기상청",
                List.of(new WeatherForecast.ShortTermForecast(Instant.parse("2026-10-04T05:00:00Z"), 18, 30, 2.5)),
                List.of(new WeatherForecast.MidTermForecast(LocalDate.of(2026, 10, 8), 10, 20, "맑음", "구름많음", 10, 20)));
        SunTimes sun =
                new SunTimes(LocalDate.of(2026, 10, 4), LocalTime.of(7, 1), LocalTime.of(18, 11), LocalTime.of(18, 38));
        when(spotDetailQueryService.findDetail(103L))
                .thenReturn(new SpotDetail(
                        103L,
                        SpotType.BAKJI,
                        "능선 끝 평지",
                        37.71,
                        128.75,
                        "강원 평창군",
                        WARNED,
                        bakji,
                        null,
                        NO_RATING,
                        List.of(),
                        List.of(
                                new SpotExpectedPeople(LocalDate.of(2026, 11, 7), 9),
                                new SpotExpectedPeople(LocalDate.of(2026, 11, 8), 4)),
                        List.of(
                                new SpotRecruitingBasecamp(
                                        41L,
                                        "설악 능선 1박",
                                        LocalDate.of(2026, 11, 7),
                                        LocalDate.of(2026, 11, 8),
                                        6,
                                        3,
                                        new SpotJoinCondition(1, 20, 30, true)),
                                new SpotRecruitingBasecamp(
                                        42L,
                                        "누구나 환영",
                                        LocalDate.of(2026, 11, 14),
                                        LocalDate.of(2026, 11, 16),
                                        4,
                                        1,
                                        new SpotJoinCondition(null, null, null, false))),
                        new SpotWeather(forecast, sun)));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/103").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "spotId": 103, "type": "BAKJI", "name": "능선 끝 평지", "lat": 37.71, "lng": 128.75,
                  "address": "강원 평창군",
                  "bakji": {
                    "description": "능선 끝 평지", "hasWater": false, "hasToilet": true, "signalLevel": "WEAK",
                    "confirmationCount": 12, "reporter": { "memberId": 31, "nickname": "새벽능선" }
                  },
                  "publicDetail": null,
                  "parkWarning": {
                    "warned": true, "areaName": "설악산 국립공원", "source": "KDPA", "sourceDate": "2026-01-01",
                    "notice": "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.",
                    "guide": "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요."
                  },
                  "rating": { "average": null, "count": 0 },
                  "recentReviews": [],
                  "expectedPeople": [{ "date": "2026-11-07", "count": 9 }, { "date": "2026-11-08", "count": 4 }],
                  "recruitingBasecamps": [
                    { "basecampId": 41, "title": "설악 능선 1박", "startDate": "2026-11-07", "endDate": "2026-11-08",
                      "capacity": 6, "headcount": 3,
                      "joinCondition": { "minTrustLevel": 1, "ageGroupMin": 20, "ageGroupMax": 30,
                                         "sameGenderOnly": true } },
                    { "basecampId": 42, "title": "누구나 환영", "startDate": "2026-11-14", "endDate": "2026-11-16",
                      "capacity": 4, "headcount": 1,
                      "joinCondition": { "minTrustLevel": null, "ageGroupMin": null, "ageGroupMax": null,
                                         "sameGenderOnly": false } }
                  ],
                  "weather": {
                    "source": "기상청",
                    "shortTerm": [
                      { "at": "2026-10-04T05:00:00Z", "temperature": 18, "precipitationProbability": 30,
                        "windSpeed": 2.5 }
                    ],
                    "midTerm": [
                      { "date": "2026-10-08", "minTemperature": 10, "maxTemperature": 20, "amSky": "맑음",
                        "pmSky": "구름많음", "amPrecipitationProbability": 10, "pmPrecipitationProbability": 20 }
                    ],
                    "sun": { "date": "2026-10-04", "sunrise": "07:01", "sunset": "18:11", "civilTwilightEnd": "18:38" }
                  }
                }
                """);
    }

    @Test
    @DisplayName("[F-05][F-09] 천문연만 실패해 sun이 없으면 weather 안에 sun 필드를 null로 남긴다")
    void keepsSunFieldAsNullWhenSunMissing() {
        // given
        WeatherForecast forecast = new WeatherForecast("기상청", List.of(), List.of());
        when(spotDetailQueryService.findDetail(104L))
                .thenReturn(new SpotDetail(
                        104L,
                        SpotType.CAMPSITE,
                        "숲속 야영장",
                        37.52,
                        127.81,
                        null,
                        NOT_WARNED,
                        null,
                        null,
                        NO_RATING,
                        List.of(),
                        List.of(),
                        List.of(),
                        new SpotWeather(forecast, null)));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/104").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.weather.source").isEqualTo("기상청");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.weather.shortTerm")
                .asList()
                .isEmpty();
        assertThat(result).bodyJson().extractingPath("$.weather").asMap().containsKey("sun");
        assertThat(result).bodyJson().extractingPath("$.weather.sun").isNull();
    }

    @Test
    @DisplayName("[F-05] 경고가 아닌 장소의 parkWarning은 warned false만 담고 다른 필드는 응답에서 뺀다")
    void notWarnedParkWarningHasOnlyWarnedField() {
        // given
        when(spotDetailQueryService.findDetail(105L))
                .thenReturn(new SpotDetail(
                        105L,
                        SpotType.CAMPSITE,
                        "숲속 야영장",
                        37.52,
                        127.81,
                        null,
                        NOT_WARNED,
                        null,
                        null,
                        NO_RATING,
                        List.of(),
                        List.of(),
                        List.of(),
                        null));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/105").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.parkWarning").isEqualTo(Map.of("warned", false));
        assertThat(result).bodyJson().extractingPath("$.weather").isNull();
    }

    @Test
    @DisplayName("[F-05] 서비스가 장소를 찾지 못하면 404 NOT_FOUND를 응답한다")
    void missingSpotReturnsNotFound() {
        // given
        when(spotDetailQueryService.findDetail(999L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult result = mvc.get().uri(PATH + "/999").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-05] 장소 ID가 숫자가 아니면 400 INVALID_INPUT과 spotId 필드 오류를 응답한다")
    void nonNumericSpotIdReturnsInvalidInput() {
        // when
        MvcTestResult result = mvc.get().uri(PATH + "/abc").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='spotId')]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(spotDetailQueryService);
    }
}
