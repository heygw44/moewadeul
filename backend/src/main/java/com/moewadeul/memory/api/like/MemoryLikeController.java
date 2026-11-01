package com.moewadeul.memory.api.like;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.memory.application.like.MemoryLikeService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MemoryLikeController {

    private final MemoryLikeService memoryLikeService;

    MemoryLikeController(MemoryLikeService memoryLikeService) {
        this.memoryLikeService = memoryLikeService;
    }

    @Operation(
            summary = "추억 기록 좋아요",
            description = "이메일 인증을 마친 회원이 공개 기록에 좋아요를 누르고, 200과 함께 liked(true)와 likeCount를 준다. "
                    + "작성자 본인도 자기 공개 기록에 누를 수 있다. "
                    + "이미 좋아요를 누른 기록에 다시 보내도 서버는 오류 없이 같은 응답을 주고 좋아요 수는 늘지 않는다. "
                    + "기록이 없거나 비공개이면 작성자에게도 404 NOT_FOUND다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, 이메일 인증을 마치지 않았으면 403 MEMBER_NOT_VERIFIED다.")
    @PutMapping("/api/memories/{memoryId}/like")
    MemoryLikeResponse like(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long memoryId) {
        return MemoryLikeResponse.from(memoryLikeService.like(loginMember.memberId(), memoryId));
    }

    @Operation(
            summary = "추억 기록 좋아요 취소",
            description = "이메일 인증을 마친 회원이 공개 기록에 눌러 둔 좋아요를 취소하고, 200과 함께 liked(false)와 likeCount를 준다. "
                    + "좋아요를 누르지 않은 기록에 보내도 서버는 오류 없이 같은 응답을 준다. "
                    + "기록이 없거나 비공개이면 작성자에게도 404 NOT_FOUND다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, 이메일 인증을 마치지 않았으면 403 MEMBER_NOT_VERIFIED다.")
    @DeleteMapping("/api/memories/{memoryId}/like")
    MemoryLikeResponse unlike(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long memoryId) {
        return MemoryLikeResponse.from(memoryLikeService.unlike(loginMember.memberId(), memoryId));
    }
}
