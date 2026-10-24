package com.moewadeul.basecamp.api.query;

import com.moewadeul.basecamp.application.query.BasecampDetailQueryService;
import com.moewadeul.basecamp.application.query.BasecampSearchService;
import com.moewadeul.basecamp.application.query.MyBasecampListService;
import com.moewadeul.common.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class BasecampQueryController {

    private final BasecampSearchService basecampSearchService;
    private final MyBasecampListService myBasecampListService;
    private final BasecampDetailQueryService basecampDetailQueryService;

    @Operation(
            summary = "베이스캠프 검색",
            description = "지도 영역이나 반경 안에서 모집 중인 베이스캠프를 출발일이 빠른 순서로 돌려준다. 로그인하지 않아도 조회할 수 있다. "
                    + "지역은 swLat, swLng, neLat, neLng 네 개(지도 영역) 또는 lat, lng, radiusKm 세 개(반경) 중 한 묶음만 모두 보내야 한다. "
                    + "위도는 -90~90, 경도는 -180~180이고, 영역은 남서쪽 값이 북동쪽 값보다 작아야 하며, 반경은 0보다 크고 50 이하여야 한다. "
                    + "fromDate와 toDate는 출발일의 범위(yyyy-MM-dd, 양 끝 포함)이고 하나만 보내도 된다. "
                    + "hasVacancy가 true이면 정원이 아직 차지 않은 베이스캠프만 돌려준다. "
                    + "지도에 보이는(ACTIVE) 장소의 모집 중(RECRUITING) 베이스캠프만 나오고, 출발일이 같으면 베이스캠프 ID 순서다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "응답에는 전체 개수가 없고, 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "로그인한 요청자의 응답에는 항목마다 canApply와 unmetReasons가 있다. unmetReasons는 신청할 수 없는 이유 코드의 목록이고, "
                    + "신청할 수 있으면 빈 목록이며 그때 canApply는 true다. 비로그인 요청자의 응답에는 두 필드가 없다. "
                    + "이유 코드는 TRUST_LEVEL(신뢰 단계 부족), AGE_GROUP(본인확인한 연령대가 없거나 범위 밖), GENDER(본인확인한 성별이 없거나 "
                    + "동성만 받는 조건과 다름), DATE_CONFLICT(같은 기간에 진행 중인 다른 베이스캠프의 ACTIVE 멤버), ALREADY_JOINED(이미 멤버이거나 대기 중인 신청이 있음), "
                    + "REAPPLY_NOT_ALLOWED(거절·탈퇴·강퇴된 적이 있음)이고, 이 순서로 나온다. "
                    + "지역이 없거나 두 묶음을 모두 보냈거나 일부만 보냈을 때, 값이 범위를 벗어났을 때, 반경이 50을 넘을 때, "
                    + "fromDate가 toDate보다 늦을 때, 날짜 형식이 틀렸을 때는 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/api/basecamps")
    BasecampSearchPageResponse search(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @ParameterObject @ModelAttribute BasecampSearchRequest request) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return BasecampSearchPageResponse.from(basecampSearchService.search(viewerId, request.toQuery()));
    }

    @Operation(
            summary = "내 베이스캠프",
            description =
                    "로그인한 회원이 지금 캠프 리더, 멤버, 신청자로 얽혀 있는 베이스캠프를 출발일이 늦은 순서로 돌려준다(출발일이 같으면 베이스캠프 ID가 큰 순서). "
                            + "이메일 인증 전의 회원도 볼 수 있다. "
                            + "relation을 생략하면 셋 모두 주고, LEADER(내가 ACTIVE 멤버이고 캠프 리더), MEMBER(내가 ACTIVE 멤버이고 역할이 멤버), "
                            + "APPLICANT(내 합류 신청이 대기 중) 중 하나를 보내면 그 관계만 준다. 탈퇴·강퇴된 베이스캠프와 거절·취소·만료된 신청은 지금 관계가 아니라서 나오지 않는다. "
                            + "status를 생략하면 모든 상태를 주고, RECRUITING, CLOSED, CONFIRMED, COMPLETED, CANCELED 중 하나를 보내면 그 상태만 준다. "
                            + "장소의 상태로는 거르지 않아서, 장소가 숨겨져도 내 기록이면 나온다. "
                            + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. 응답에는 전체 개수가 없고 다음 페이지가 있는지만 hasNext로 알려 준다. "
                            + "항목마다 basecampId, title, spot(spotId, name, type), startDate, endDate, capacity, headcount(캠프 리더를 포함한 ACTIVE 멤버 수), "
                            + "status, myRelation(LEADER, MEMBER, APPLICANT 중 하나)이 있다. "
                            + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, relation이나 status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다.")
    @GetMapping("/api/me/basecamps")
    MyBasecampPageResponse listMine(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @ParameterObject @ModelAttribute MyBasecampListRequest request) {
        return MyBasecampPageResponse.from(myBasecampListService.list(loginMember.memberId(), request.toQuery()));
    }

    @Operation(
            summary = "베이스캠프 상세",
            description = "베이스캠프 하나의 기본 정보, 합류 조건, 멤버, 요청자와의 관계(myRelation)를 돌려준다. 로그인하지 않아도 조회할 수 있고, "
                    + "모집 중이 아닌 베이스캠프(마감, 확정, 완료, 취소)도 조회할 수 있다. "
                    + "멤버는 ACTIVE 멤버를 캠프 리더 먼저, 그다음 합류한 순서로 준다. "
                    + "비로그인 요청자의 응답에서는 leader와 members에 memberId, nickname(members는 role 포함)만 있고, myRelation은 NONE이다. "
                    + "로그인한 요청자의 응답에서는 members에 연령대·성별(각각 본인확인한 값인지 verified로 구분), 신뢰 단계, "
                    + "완료한 동행 횟수가 더해지고, leader에는 신뢰 단계가 더해진다. 연령대는 TWENTIES, THIRTIES, FORTIES, FIFTIES, SIXTIES_PLUS 같은 이름이다. "
                    + "myRelation은 NONE, APPLICANT(대기 중인 신청이 있음), MEMBER, LEADER 중 하나다. "
                    + "contactInfo는 베이스캠프가 확정된 뒤부터 완료 후 7일까지 ACTIVE 멤버(캠프 리더 포함)에게, "
                    + "모집 중이거나 마감된 동안에는 캠프 리더에게만, 등록된 값이 있을 때만 응답에 나타난다. "
                    + "그 밖의 요청자에게는 필드 자체가 없다. safetyNotice는 항상 있다. 이메일과 출생연도는 어떤 경우에도 응답하지 않는다. "
                    + "베이스캠프가 없으면 404 NOT_FOUND로, basecampId가 숫자가 아니면 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/api/basecamps/{basecampId}")
    BasecampDetailResponse find(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long basecampId) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return BasecampDetailResponse.from(basecampDetailQueryService.find(viewerId, basecampId), viewerId != null);
    }
}
