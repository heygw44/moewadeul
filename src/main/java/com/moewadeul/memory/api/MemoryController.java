package com.moewadeul.memory.api;

import com.moewadeul.common.idempotency.IdempotencyExecutor;
import com.moewadeul.common.idempotency.IdempotentRequest;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.memory.application.MemoryCommandService;
import com.moewadeul.memory.application.MemoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MemoryController {

    private final MemoryCommandService memoryCommandService;
    private final MemoryQueryService memoryQueryService;
    private final IdempotencyExecutor idempotencyExecutor;

    MemoryController(
            MemoryCommandService memoryCommandService,
            MemoryQueryService memoryQueryService,
            IdempotencyExecutor idempotencyExecutor) {
        this.memoryCommandService = memoryCommandService;
        this.memoryQueryService = memoryQueryService;
        this.idempotencyExecutor = idempotencyExecutor;
    }

    @Operation(
            summary = "추억 기록 등록",
            description = "이메일 인증을 마친 회원이 다녀온 곳을 기록한다. 201과 함께 memoryId를 준다. "
                    + "Idempotency-Key 헤더가 필요하다. 없으면 400 IDEMPOTENCY_KEY_REQUIRED, 영문·숫자·'-'·'_' 1~64자가 아니면 400 INVALID_INPUT이다. "
                    + "같은 키로 다시 보내면 기록을 새로 만들지 않고 처음 결과를 그대로 돌려주고, 처음 요청이 아직 처리 중이면 409 IDEMPOTENCY_IN_PROGRESS이다. "
                    + "lat(-90~90), lng(-180~180), visitedDate(한국 날짜, 오늘보다 뒤일 수 없음), nights(0~30)는 꼭 보낸다. "
                    + "content는 선택이고, 보내면 공백뿐일 수 없으며 2,000자 이하다. visibility는 PRIVATE(기본)나 PUBLIC이다. "
                    + "spotId를 보내면 지도에 보이는(ACTIVE) 장소여야 하고, 좌표는 보낸 값을 그대로 저장한다. "
                    + "imageIds는 최대 10개이고, 회원이 올렸고 다른 기록에 붙지 않았으며 저장소에 올라간 사진이어야 한다. "
                    + "규칙을 어기면 400 INVALID_INPUT이고 fieldErrors에 어긴 필드가 있다.")
    @PostMapping("/api/memories")
    @ResponseStatus(HttpStatus.CREATED)
    MemoryCreatedResponse write(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MemoryCreateRequest request) {
        long memberId = loginMember.memberId();
        return idempotencyExecutor.execute(
                new IdempotentRequest(memberId, idempotencyKey, "POST /api/memories", request),
                HttpStatus.CREATED,
                MemoryCreatedResponse.class,
                () -> new MemoryCreatedResponse(memoryCommandService.write(memberId, request.toCommand())));
    }

    @Operation(
            summary = "공개 추억 피드",
            description = "모든 회원의 공개(PUBLIC) 기록을 새로 쓴 것부터 준다. 로그인하지 않아도 볼 수 있고 비공개 기록은 나오지 않는다. "
                    + "page(0부터, 기본 0)와 size(1~50, 기본 20)를 받고, 전체 개수 없이 content, page, size, hasNext를 준다. "
                    + "피드는 누구에게나 같은 모양이라 작성자 본인의 기록이어도 정확한 좌표 없이 소수 둘째 자리로 반올림한 "
                    + "approxLat, approxLng만 준다. 연결한 장소가 지도에 보이지 않으면 spot을, 글이 없으면 excerpt를 뺀다. "
                    + "excerpt는 글 앞 100자다. 사진은 모두 주지 않고 thumbnailUrl과 imageCount만 준다. "
                    + "thumbnailUrl은 기록 안 순서가 가장 앞선 사진의 썸네일 조회 URL이고, 썸네일 없이 올린 사진이면 원본 조회 URL이다. "
                    + "1시간 동안 쓸 수 있고, 사진이 없으면 필드를 뺀다. imageCount는 사진 수이고 없으면 0이다. "
                    + "likedByMe는 로그인한 사람이 조회할 때만 주고, 비회원이면 필드를 뺀다.")
    @GetMapping("/api/memories/feed")
    MemoryFeedPageResponse feed(@AuthenticationPrincipal LoginMember loginMember, @Valid MemoryFeedRequest request) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return MemoryFeedPageResponse.from(
                memoryQueryService.listFeed(request.pageOrDefault(), request.sizeOrDefault(), viewerId));
    }

    @Operation(
            summary = "추억 기록 상세",
            description = "기록 한 건을 준다. 로그인하지 않아도 공개(PUBLIC) 기록은 볼 수 있다. "
                    + "비공개(PRIVATE) 기록을 작성자가 아닌 사람이 조회하면 기록이 없는 것과 같은 404 NOT_FOUND다. "
                    + "작성자는 회원 ID와 닉네임만 준다. 정확한 좌표(lat, lng)는 작성자에게만 주고, 작성자가 아닌 사람(비회원 포함)에게는 "
                    + "필드를 빼고 소수 둘째 자리로 반올림한 approxLat, approxLng만 준다. "
                    + "likeCount는 모두에게 주고, likedByMe는 로그인한 사람이 조회할 때만 주며 비회원이면 필드를 뺀다. "
                    + "연결한 장소가 지도에 보이지 않으면 spot을, 글이 없으면 content를 뺀다. "
                    + "images는 기록 안 순서대로이고, url은 1시간 동안 쓸 수 있는 조회 URL이다.")
    @GetMapping("/api/memories/{memoryId}")
    MemoryDetailResponse detail(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long memoryId) {
        Long viewerId = loginMember == null ? null : loginMember.memberId();
        return MemoryDetailResponse.from(memoryQueryService.findVisible(memoryId, viewerId));
    }

    @Operation(
            summary = "추억 기록 수정",
            description = "작성자가 자기 기록을 고친다. 모든 필드가 선택이고 보내지 않은 필드는 그대로 둔다. "
                    + "spotId를 null로 보내면 장소 연결을 끊고, content를 null로 보내면 글을 지운다. 나머지 필드의 null은 400이다. "
                    + "lat과 lng는 함께 보내야 한다. 공개 범위(visibility)는 언제든 바꿀 수 있다. "
                    + "imageIds를 보내면 사진을 그 순서대로 통째로 바꾸고, 빠진 사진은 기록에서 떨어진다. "
                    + "응답은 200과 함께 고친 뒤의 기록 상세다. "
                    + "기록이 없으면 404 NOT_FOUND, 다른 회원의 기록이면 403 ACCESS_DENIED다.")
    @PatchMapping("/api/memories/{memoryId}")
    MemoryDetailResponse update(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long memoryId,
            @RequestBody MemoryUpdateRequest request) {
        return MemoryDetailResponse.from(
                memoryCommandService.revise(loginMember.memberId(), memoryId, request.toCommand()));
    }

    @Operation(
            summary = "추억 기록 삭제",
            description = "작성자가 자기 기록을 지우고 204로 응답한다. 붙은 사진은 기록에서 떨어지고, 서버의 정리 작업이 24시간 뒤에 지운다. "
                    + "기록이 없으면 404 NOT_FOUND, 다른 회원의 기록이면 403 ACCESS_DENIED다.")
    @DeleteMapping("/api/memories/{memoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long memoryId) {
        memoryCommandService.delete(loginMember.memberId(), memoryId);
    }

    @Operation(
            summary = "내 추억 기록",
            description = "로그인한 회원의 기록을 공개 범위와 상관없이 모두 정확한 좌표와 함께 준다. 이메일 인증 전이어도 볼 수 있다. "
                    + "방문일이 늦은 순서이고 방문일이 같으면 memoryId가 큰 것이 먼저다. 지도에 한 번에 그리므로 페이지 없이 배열로 준다. "
                    + "excerpt는 글 앞 100자, thumbnailUrl은 첫 사진의 썸네일 조회 URL(썸네일이 없으면 원본 조회 URL)이고, 없으면 필드를 뺀다.")
    @GetMapping("/api/me/memories")
    List<MemorySummaryResponse> listMine(@AuthenticationPrincipal LoginMember loginMember) {
        return memoryQueryService.listMine(loginMember.memberId()).stream()
                .map(MemorySummaryResponse::from)
                .toList();
    }

    @Operation(
            summary = "회원의 추억지도",
            description = "회원 한 명의 공개(PUBLIC) 기록을 방문일이 늦은 순서로 준다. 로그인하지 않아도 볼 수 있다. "
                    + "방문일이 같으면 memoryId가 큰 것이 먼저다. 지도에 한 번에 그리므로 페이지 없이 배열로 준다. "
                    + "조회하는 사람이 그 회원 본인이어도 공개 기록만, 소수 둘째 자리로 반올림한 approxLat, approxLng만 준다. "
                    + "본인의 전체 기록은 내 추억 기록 목록으로 읽는다. "
                    + "회원이 없거나 탈퇴했으면 404 NOT_FOUND다. "
                    + "excerpt는 글 앞 100자, thumbnailUrl은 첫 사진의 썸네일 조회 URL(썸네일이 없으면 원본 조회 URL)이고, 없으면 필드를 뺀다.")
    @GetMapping("/api/members/{memberId}/memories")
    List<PublicMemorySummaryResponse> listOfMember(@PathVariable long memberId) {
        return memoryQueryService.listPublicOf(memberId).stream()
                .map(PublicMemorySummaryResponse::from)
                .toList();
    }
}
