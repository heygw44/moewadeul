package com.moewadeul.trip.api;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.idempotency.application.IdempotencyExecutor;
import com.moewadeul.idempotency.application.IdempotentRequest;
import com.moewadeul.trip.application.SoloTripCommandService;
import com.moewadeul.trip.application.SoloTripQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SoloTripController {

    private final SoloTripCommandService soloTripCommandService;
    private final SoloTripQueryService soloTripQueryService;
    private final IdempotencyExecutor idempotencyExecutor;

    SoloTripController(
            SoloTripCommandService soloTripCommandService,
            SoloTripQueryService soloTripQueryService,
            IdempotencyExecutor idempotencyExecutor) {
        this.soloTripCommandService = soloTripCommandService;
        this.soloTripQueryService = soloTripQueryService;
        this.idempotencyExecutor = idempotencyExecutor;
    }

    @Operation(
            summary = "개인 일정 등록",
            description = "이메일 인증을 마친 회원이 혼자 가는 야영 일정을 등록한다. 201과 함께 soloTripId를 준다. "
                    + "Idempotency-Key 헤더가 필요하다. 없으면 400 IDEMPOTENCY_KEY_REQUIRED, 영문·숫자·'-'·'_' 1~64자가 아니면 400 INVALID_INPUT이다. "
                    + "같은 키로 다시 보내면 일정을 새로 만들지 않고 처음 결과를 그대로 돌려주고, 처음 요청이 아직 처리 중이면 409 IDEMPOTENCY_IN_PROGRESS이다. "
                    + "날짜는 한국 날짜다. 출발일은 내일부터 60일 안이고 종료일은 출발일의 1~3일 뒤여야 하며, 어기거나 빠지면 400 SOLO_TRIP_SCHEDULE_INVALID다. "
                    + "spotId가 없으면 400 INVALID_INPUT이다. 종료일이 오늘보다 뒤인 내 일정이 이미 3개면 409 SOLO_TRIP_LIMIT, "
                    + "그중 하나와 밤이 하루라도 겹치면 409 SOLO_TRIP_OVERLAP이다. 앞 일정의 종료일에 출발하는 일정은 겹치지 않는다. "
                    + "장소가 없거나 지도에 보이는(ACTIVE) 상태가 아니면 404 NOT_FOUND다. 공원 경계 경고 박지도 등록할 수 있다. "
                    + "일정은 본인만 보고, 다른 사람에게는 장소 상세의 예상 인원으로만 드러난다.")
    @PostMapping("/api/solo-trips")
    @ResponseStatus(HttpStatus.CREATED)
    SoloTripRegisteredResponse register(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SoloTripRegisterRequest request) {
        long memberId = loginMember.memberId();
        return idempotencyExecutor.execute(
                new IdempotentRequest(memberId, idempotencyKey, "POST /api/solo-trips", request),
                HttpStatus.CREATED,
                SoloTripRegisteredResponse.class,
                () -> new SoloTripRegisteredResponse(soloTripCommandService.register(memberId, request.toCommand())));
    }

    @Operation(
            summary = "내 개인 일정",
            description = "로그인한 회원의 개인 일정 중 종료일이 오늘(한국 날짜)보다 뒤인 것을 출발일이 빠른 순서로 준다. "
                    + "이메일 인증 전이어도 볼 수 있다. 진행 예정 일정은 최대 3개라서 페이지 없이 배열로 준다. "
                    + "장소가 숨겨져도 내 기록이라서 보여 준다.")
    @GetMapping("/api/me/solo-trips")
    List<SoloTripItemResponse> listMine(@AuthenticationPrincipal LoginMember loginMember) {
        return soloTripQueryService.listUpcoming(loginMember.memberId()).stream()
                .map(SoloTripItemResponse::from)
                .toList();
    }

    @Operation(
            summary = "개인 일정 삭제",
            description = "등록한 회원이 자기 일정을 지우고 204로 응답한다. 지난 일정도 지울 수 있다. "
                    + "일정이 없으면 404 NOT_FOUND, 다른 회원의 일정이면 403 ACCESS_DENIED다.")
    @DeleteMapping("/api/solo-trips/{soloTripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long soloTripId) {
        soloTripCommandService.delete(loginMember.memberId(), soloTripId);
    }
}
