package com.moewadeul.aisearch.api;

import com.moewadeul.aisearch.application.AiInterpretService;
import com.moewadeul.common.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AiInterpretController {

    private final AiInterpretService aiInterpretService;

    AiInterpretController(AiInterpretService aiInterpretService) {
        this.aiInterpretService = aiInterpretService;
    }

    @Operation(
            summary = "AI 문장 해석",
            description = "이메일 인증을 마친 회원이 쓴 문장을 장소 검색 조건으로 바꿔 돌려준다. 장소를 고르거나 합법 여부를 판단하지 않고, "
                    + "조건만 읽어 낸다. 화면은 이 조건으로 지역 검색과 기존 조회 API를 불러 장소를 보여 준다. "
                    + "query는 앞뒤 공백을 뺀 길이가 2~100자여야 하고, 어기면 400 INVALID_INPUT이다. 응답 본문과 로그에 문장을 담지 않는다. "
                    + "응답의 region(지역 이름), radiusKm(반경, 최대 50), types(CAMPSITE·FOREST·BAKJI), hasWater, hasToilet, "
                    + "excludeWarning, hideClosed(true일 때만), settings(입지)는 읽어 낸 조건만 담고 나머지 키는 뺀다. "
                    + "unparsed는 조건으로 바꾸지 못한 표현이고 항상 담으며 없으면 빈 배열이다. "
                    + "로그인하지 않으면 401 AUTHENTICATION_REQUIRED, 이메일 인증 전이면 403 MEMBER_NOT_VERIFIED다. "
                    + "서버는 서비스 전체의 하루 비용 한도를 먼저 보고, 한도에 닿았으면 503 AI_SEARCH_UNAVAILABLE을 준다. 이 요청은 회원 횟수에 세지 않는다. "
                    + "그다음 회원이 한국 날짜 하루에 20번을 넘으면 429 AI_SEARCH_LIMITED와 다음 날 0시까지 남은 초를 Retry-After로 준다. 이 요청도 세지 않는다. "
                    + "횟수를 센 뒤 OpenAI 호출·응답 검증이 실패하면 503 AI_SEARCH_UNAVAILABLE이고, 이 요청은 그날 횟수에 남는다.")
    @PostMapping("/api/spots/ai-interpret")
    AiInterpretResponse interpret(
            @AuthenticationPrincipal LoginMember loginMember, @Valid @RequestBody AiInterpretRequest request) {
        return AiInterpretResponse.from(aiInterpretService.interpret(loginMember.memberId(), request.query()));
    }
}
