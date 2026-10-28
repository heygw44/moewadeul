package com.moewadeul.basecamp.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.basecamp.application.JoinConditionSummary;
import com.moewadeul.basecamp.application.JoinEligibility;
import com.moewadeul.basecamp.application.query.BasecampDetail;
import com.moewadeul.basecamp.application.query.BasecampDetailQueryService;
import com.moewadeul.basecamp.application.query.BasecampSearchItem;
import com.moewadeul.basecamp.application.query.BasecampSearchPage;
import com.moewadeul.basecamp.application.query.BasecampSearchQuery;
import com.moewadeul.basecamp.application.query.BasecampSearchService;
import com.moewadeul.basecamp.application.query.BasecampSpotType;
import com.moewadeul.basecamp.application.query.MyBasecampListQuery;
import com.moewadeul.basecamp.application.query.MyBasecampListService;
import com.moewadeul.basecamp.application.query.MyBasecampPage;
import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.basecamp.domain.JoinUnmetReason;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(BasecampQueryController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class BasecampQueryControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String BASECAMPS = "/api/basecamps";
    private static final String MY_BASECAMPS = "/api/me/basecamps";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BasecampSearchService basecampSearchService;

    @MockitoBean
    private BasecampDetailQueryService basecampDetailQueryService;

    @MockitoBean
    private MyBasecampListService myBasecampListService;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSearchParameters")
    @DisplayName("[F-12][F-31] 검색 파라미터가 어긋나면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void searchRejectsInvalidParameters(String description, String query) {
        // when
        MvcTestResult result = mvc.get().uri(BASECAMPS + "?" + query).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(basecampSearchService);
    }

    static Stream<Arguments> invalidSearchParameters() {
        String area = "swLat=37.0&swLng=127.0&neLat=38.0&neLng=128.0";
        String radius = "lat=37.25&lng=127.25&radiusKm=10";
        return Stream.of(
                Arguments.of("지역 없음", "page=0"),
                Arguments.of("영역 일부만", "swLat=37.0&swLng=127.0"),
                Arguments.of("반경 일부만", "lat=37.25&lng=127.25"),
                Arguments.of("영역과 반경 모두", area + "&" + radius),
                Arguments.of("영역과 반경 일부", area + "&radiusKm=10"),
                Arguments.of("반경 50km 초과", "lat=37.25&lng=127.25&radiusKm=50.1"),
                Arguments.of("반경 0", "lat=37.25&lng=127.25&radiusKm=0"),
                Arguments.of("위도 범위 밖", "lat=91&lng=127.25&radiusKm=10"),
                Arguments.of("남서쪽 위도가 북동쪽 이상", "swLat=38.0&swLng=127.0&neLat=37.0&neLng=128.0"),
                Arguments.of("남서쪽 경도가 북동쪽 이상", "swLat=37.0&swLng=128.0&neLat=38.0&neLng=127.0"),
                Arguments.of("출발일 범위 역순", radius + "&fromDate=2026-10-25&toDate=2026-10-20"),
                Arguments.of("날짜 형식 오류", radius + "&fromDate=20261020"),
                Arguments.of("페이지 크기 51", radius + "&size=51"),
                Arguments.of("페이지 크기 0", radius + "&size=0"),
                Arguments.of("페이지 번호 음수", radius + "&page=-1"),
                Arguments.of("박 수 0", radius + "&nights=0"),
                Arguments.of("박 수 4", radius + "&nights=1,4"),
                Arguments.of("최소 신뢰 단계 상한 0", radius + "&maxTrustLevel=0"),
                Arguments.of("최소 신뢰 단계 상한 3", radius + "&maxTrustLevel=3"),
                Arguments.of("모르는 장소 유형", radius + "&types=CAMPSITE,PARK"));
    }

    @Test
    @DisplayName("[F-12] 반경 50km와 출발일 범위를 보내면 받아서 기본값(page 0, size 20)과 함께 서비스에 넘긴다")
    void searchPassesRadiusQueryWithDefaults() {
        // given
        when(basecampSearchService.search(any(), any())).thenReturn(new BasecampSearchPage(List.of(), 0, 20, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(BASECAMPS
                        + "?lat=37.25&lng=127.25&radiusKm=50&fromDate=2026-10-20&toDate=2026-10-25&hasVacancy=true")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        ArgumentCaptor<BasecampSearchQuery> captor = ArgumentCaptor.forClass(BasecampSearchQuery.class);
        verify(basecampSearchService).search(eq(null), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BasecampSearchQuery(
                        new BasecampSearchQuery.Radius(37.25, 127.25, 50.0),
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 25),
                        true,
                        Set.of(),
                        Set.of(),
                        null,
                        false,
                        false,
                        0,
                        20));
    }

    @Test
    @DisplayName("[F-12] 지도 영역을 보내면 영역 조건으로 서비스에 넘기고, 로그인한 회원의 ID를 함께 넘긴다")
    void searchPassesAreaQueryWithViewerId() {
        // given
        when(basecampSearchService.search(any(), any())).thenReturn(new BasecampSearchPage(List.of(), 1, 50, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(BASECAMPS + "?swLat=37.0&swLng=127.0&neLat=38.0&neLng=128.0&page=1&size=50")
                .with(verified())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        ArgumentCaptor<BasecampSearchQuery> captor = ArgumentCaptor.forClass(BasecampSearchQuery.class);
        verify(basecampSearchService).search(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BasecampSearchQuery(
                        new BasecampSearchQuery.Area(37.0, 127.0, 38.0, 128.0),
                        null,
                        null,
                        false,
                        Set.of(),
                        Set.of(),
                        null,
                        false,
                        false,
                        1,
                        50));
    }

    @Test
    @DisplayName("[F-31] 상세 조건(장소 유형, 박 수, 최소 신뢰 단계 상한, 연령 제한 없음, 동성만 아님)을 받아서 서비스에 넘긴다")
    void searchPassesDetailConditions() {
        // given
        when(basecampSearchService.search(any(), any())).thenReturn(new BasecampSearchPage(List.of(), 0, 20, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(BASECAMPS
                        + "?lat=37.25&lng=127.25&radiusKm=10&types=BAKJI,FOREST&nights=1&nights=2"
                        + "&maxTrustLevel=1&noAgeLimit=true&noSameGenderOnly=true")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        ArgumentCaptor<BasecampSearchQuery> captor = ArgumentCaptor.forClass(BasecampSearchQuery.class);
        verify(basecampSearchService).search(eq(null), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BasecampSearchQuery(
                        new BasecampSearchQuery.Radius(37.25, 127.25, 10.0),
                        null,
                        null,
                        false,
                        Set.of(BasecampSpotType.BAKJI, BasecampSpotType.FOREST),
                        Set.of(1, 2),
                        1,
                        true,
                        true,
                        0,
                        20));
    }

    @Test
    @DisplayName("[F-12][NFR-11] 비로그인 검색 응답에는 canApply와 unmetReasons 필드가 없다")
    void anonymousSearchOmitsEligibilityFields() {
        // given
        when(basecampSearchService.search(any(), any()))
                .thenReturn(new BasecampSearchPage(List.of(searchItem(null)), 0, 20, true));

        // when
        MvcTestResult result =
                mvc.get().uri(BASECAMPS + "?lat=37.25&lng=127.25&radiusKm=10").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo(77);
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.type").isEqualTo("BAKJI");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].joinCondition.sameGenderOnly")
                .isEqualTo(false);
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].canApply");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].unmetReasons");
    }

    @Test
    @DisplayName("[F-12] 로그인한 검색 응답에는 canApply와 unmetReasons가 있다")
    void loggedInSearchIncludesEligibilityFields() {
        // given
        JoinEligibility unmet =
                new JoinEligibility(false, List.of(JoinUnmetReason.TRUST_LEVEL, JoinUnmetReason.GENDER));
        JoinEligibility ok = new JoinEligibility(true, List.of());
        when(basecampSearchService.search(any(), any()))
                .thenReturn(new BasecampSearchPage(List.of(searchItem(unmet), searchItem(ok)), 0, 20, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(BASECAMPS + "?lat=37.25&lng=127.25&radiusKm=10")
                .with(unverified())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].canApply").isEqualTo(false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("TRUST_LEVEL", "GENDER"));
        assertThat(result).bodyJson().extractingPath("$.content[1].canApply").isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[1].unmetReasons")
                .isEqualTo(List.of());
    }

    @Test
    @DisplayName("[F-12][NFR-11] 비로그인 상세 응답은 멤버를 닉네임과 역할만 담고 myRelation이 NONE이며, 연락 수단·신뢰 정보 필드가 없다")
    void anonymousDetailShowsOnlyNicknames() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L))).thenReturn(detail(BasecampRelation.NONE, null));

        // when
        MvcTestResult result = mvc.get().uri(BASECAMPS + "/77").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(basecampDetailQueryService).find(eq(null), eq(77L));
        assertThat(result).bodyJson().extractingPath("$.myRelation").isEqualTo("NONE");
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("함께 가요");
        assertThat(result).bodyJson().extractingPath("$.leader.nickname").isEqualTo("새벽능선");
        assertThat(result).bodyJson().extractingPath("$.members[1].role").isEqualTo("MEMBER");
        assertThat(result).bodyJson().extractingPath("$.safetyNotice").isNotNull();
        assertThat(result).bodyJson().doesNotHavePath("$.leader.trustLevel");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].ageGroup");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].gender");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].trustLevel");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].completedCompanions");
        assertThat(result).bodyJson().doesNotHavePath("$.contactInfo");
    }

    @Test
    @DisplayName("[F-12][NFR-11] 로그인한 상세 응답은 멤버의 프로필 요약을 담고, 연락 수단이 없으면 contactInfo 필드를 뺀다")
    void loggedInDetailShowsProfileAndOmitsMissingContact() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L))).thenReturn(detail(BasecampRelation.NONE, null));

        // when
        MvcTestResult result =
                mvc.get().uri(BASECAMPS + "/77").with(unverified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(basecampDetailQueryService).find(eq(MEMBER_ID), eq(77L));
        assertThat(result).bodyJson().extractingPath("$.leader.trustLevel").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.members[0].ageGroup").isEqualTo("THIRTIES");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.members[0].ageGroupVerified")
                .isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.members[0].completedCompanions")
                .isEqualTo(5);
        assertThat(result).bodyJson().doesNotHavePath("$.contactInfo");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].email");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].birthYear");
    }

    @Test
    @DisplayName("[F-12] 서비스가 신청 자격을 주면 로그인한 상세 응답에 canApply와 unmetReasons가 있고, 주지 않으면 필드가 없다")
    void loggedInDetailIncludesEligibilityOnlyWhenServiceGivesIt() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L)))
                .thenReturn(detail(
                        BasecampRelation.NONE, null, new JoinEligibility(false, List.of(JoinUnmetReason.TRUST_LEVEL))));
        when(basecampDetailQueryService.find(any(), eq(78L))).thenReturn(detail(BasecampRelation.NONE, null));

        // when
        MvcTestResult with = mvc.get().uri(BASECAMPS + "/77").with(unverified()).exchange();
        MvcTestResult without =
                mvc.get().uri(BASECAMPS + "/78").with(unverified()).exchange();

        // then
        assertThat(with).bodyJson().extractingPath("$.canApply").isEqualTo(false);
        assertThat(with).bodyJson().extractingPath("$.unmetReasons").isEqualTo(List.of("TRUST_LEVEL"));
        assertThat(without).bodyJson().doesNotHavePath("$.canApply");
        assertThat(without).bodyJson().doesNotHavePath("$.unmetReasons");
    }

    @Test
    @DisplayName("[F-12][BC-23] 서비스가 연락 수단을 주면 로그인한 상세 응답에 contactInfo가 있다")
    void loggedInDetailIncludesContactWhenServiceGivesIt() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L)))
                .thenReturn(detail(BasecampRelation.MEMBER, "https://open.kakao.com/o/abc"));

        // when
        MvcTestResult result = mvc.get().uri(BASECAMPS + "/77").with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.myRelation").isEqualTo("MEMBER");
        assertThat(result).bodyJson().extractingPath("$.contactInfo").isEqualTo("https://open.kakao.com/o/abc");
    }

    @Test
    @DisplayName("[F-12][BC-09] 서비스가 장소 요약을 주지 않으면 비로그인·로그인 상세 응답 모두 spot 필드를 뺀다")
    void detailOmitsSpotWhenServiceGivesNone() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L)))
                .thenReturn(withoutSpot(detail(BasecampRelation.NONE, null)));

        // when
        MvcTestResult anonymous = mvc.get().uri(BASECAMPS + "/77").exchange();
        MvcTestResult loggedIn =
                mvc.get().uri(BASECAMPS + "/77").with(unverified()).exchange();

        // then
        for (MvcTestResult result : new MvcTestResult[] {anonymous, loggedIn}) {
            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).bodyJson().doesNotHavePath("$.spot");
            assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("굴업도 주말 1박");
        }
    }

    @Test
    @DisplayName("[F-12] 없는 베이스캠프를 조회하면 404 NOT_FOUND이고, 숫자가 아닌 ID는 400 INVALID_INPUT이다")
    void detailNotFoundAndInvalidId() {
        // given
        when(basecampDetailQueryService.find(any(), eq(404L)))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult missing = mvc.get().uri(BASECAMPS + "/404").exchange();
        MvcTestResult notNumber = mvc.get().uri(BASECAMPS + "/abc").exchange();

        // then
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(notNumber).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(notNumber).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-13] 내 베이스캠프의 relation과 status를 생략하면 거르지 않고, page는 0, size는 20으로 서비스를 부른다")
    void listMineUsesDefaults() {
        // given
        when(myBasecampListService.list(eq(MEMBER_ID), any())).thenReturn(new MyBasecampPage(List.of(), 0, 20, false));

        // when
        MvcTestResult result = mvc.get().uri(MY_BASECAMPS).with(unverified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .isStrictlyEqualTo("{ \"content\": [], \"page\": 0, \"size\": 20, \"hasNext\": false }");
        verify(myBasecampListService).list(MEMBER_ID, new MyBasecampListQuery(null, null, 0, 20));
    }

    @Test
    @DisplayName("[F-13] 내 베이스캠프의 relation과 status를 보내면 그대로 서비스에 넘긴다")
    void listMinePassesFilters() {
        // given
        when(myBasecampListService.list(eq(MEMBER_ID), any())).thenReturn(new MyBasecampPage(List.of(), 2, 5, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(MY_BASECAMPS + "?relation=APPLICANT&status=CLOSED&page=2&size=5")
                .with(unverified())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(myBasecampListService)
                .list(MEMBER_ID, new MyBasecampListQuery(BasecampRelation.APPLICANT, BasecampStatus.CLOSED, 2, 5));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "relation=UNKNOWN",
                "relation=leader",
                "relation=NONE",
                "status=UNKNOWN",
                "status=recruiting",
                "size=0",
                "size=51",
                "page=-1"
            })
    @DisplayName("[F-13] 내 베이스캠프의 relation·status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다")
    void listMineRejectsInvalidParameters(String query) {
        // when
        MvcTestResult result =
                mvc.get().uri(MY_BASECAMPS + "?" + query).with(unverified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").asList().isNotEmpty();
        verifyNoInteractions(myBasecampListService);
    }

    @Test
    @DisplayName("[F-13] 내 베이스캠프의 relation이 틀리면 고를 수 있는 LEADER, MEMBER, APPLICANT만 알려 준다")
    void listMineNamesAllowedRelationValues() {
        // when
        MvcTestResult result = mvc.get()
                .uri(MY_BASECAMPS + "?relation=NONE")
                .with(unverified())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("relation");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].reason")
                .isEqualTo("LEADER, MEMBER, APPLICANT 중 하나여야 해요.");
    }

    @Test
    @DisplayName("[F-13] 내 베이스캠프의 status를 enum으로 바꾸지 못하면 status 필드에 허용 값을 알려 준다")
    void listMineNamesAllowedStatusValues() {
        // when
        MvcTestResult result = mvc.get()
                .uri(MY_BASECAMPS + "?status=UNKNOWN")
                .with(unverified())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("status");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].reason")
                .isEqualTo("RECRUITING, CLOSED, CONFIRMED, COMPLETED, CANCELED 중 하나여야 해요.");
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않고 내 베이스캠프를 조회하면 401 AUTHENTICATION_REQUIRED이다")
    void listMineRequiresLogin() {
        // when
        MvcTestResult result = mvc.get().uri(MY_BASECAMPS).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(myBasecampListService);
    }

    private static BasecampSearchItem searchItem(JoinEligibility eligibility) {
        return new BasecampSearchItem(
                77L,
                "굴업도 주말 1박",
                new BasecampSearchItem.SpotSummary(101L, "개머리언덕", "BAKJI", 37.25, 127.25),
                LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 8),
                4,
                3,
                "RECRUITING",
                new JoinConditionSummary(null, null, null, false),
                eligibility);
    }

    private static BasecampDetail detail(BasecampRelation relation, String contactInfo) {
        return detail(relation, contactInfo, null);
    }

    private static BasecampDetail detail(BasecampRelation relation, String contactInfo, JoinEligibility eligibility) {
        return new BasecampDetail(
                77L,
                "굴업도 주말 1박",
                "함께 가요",
                new BasecampDetail.SpotSummary(101L, "개머리언덕", "BAKJI", 37.0712, 125.9893),
                LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 8),
                4,
                2,
                "CONFIRMED",
                new JoinConditionSummary(1, null, null, false),
                new BasecampDetail.DetailMember(31L, "새벽능선", "LEADER", "THIRTIES", true, "FEMALE", true, 2, 5),
                List.of(
                        new BasecampDetail.DetailMember(31L, "새벽능선", "LEADER", "THIRTIES", true, "FEMALE", true, 2, 5),
                        new BasecampDetail.DetailMember(32L, "달빛야영", "MEMBER", null, false, null, false, 1, 0)),
                relation,
                contactInfo,
                eligibility);
    }

    private static BasecampDetail withoutSpot(BasecampDetail detail) {
        return new BasecampDetail(
                detail.basecampId(),
                detail.title(),
                detail.description(),
                null,
                detail.startDate(),
                detail.endDate(),
                detail.capacity(),
                detail.headcount(),
                detail.status(),
                detail.joinCondition(),
                detail.leader(),
                detail.members(),
                detail.myRelation(),
                detail.contactInfo(),
                detail.eligibility());
    }

    private static RequestPostProcessor verified() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }

    private static RequestPostProcessor unverified() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
