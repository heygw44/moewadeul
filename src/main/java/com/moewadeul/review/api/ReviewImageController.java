package com.moewadeul.review.api;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.review.application.ReviewImageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReviewImageController {

    private final ReviewImageUploadService reviewImageUploadService;

    ReviewImageController(ReviewImageUploadService reviewImageUploadService) {
        this.reviewImageUploadService = reviewImageUploadService;
    }

    @Operation(
            summary = "후기 이미지 업로드 URL 발급",
            description = "이메일 인증을 마친 회원이 장소 후기에 붙일 이미지를 올릴 URL을 받는다. 서버는 파일을 받지 않는다. "
                    + "contentType은 image/jpeg, image/png, image/webp 중 하나이고, sizeBytes는 1~5242880(5MB)이다. 어기면 400 INVALID_INPUT이다. "
                    + "201과 함께 imageId, uploadUrl, method(PUT), headers(Content-Type, Content-Length), expiresAt을 준다. "
                    + "화면은 uploadUrl로 파일을 PUT하면서 headers를 그대로 보낸다. 서명에 형식과 크기가 들어 있어서 다르게 올리면 저장소가 거부한다. "
                    + "URL은 10분 동안 쓸 수 있다. 올린 뒤 후기 작성·수정 요청의 imageIds에 imageId를 넣는다. 후기 하나에 3장까지 붙인다. "
                    + "24시간 안에 후기에 붙이지 않은 이미지는 서버가 지운다.")
    @PostMapping("/api/reviews/images")
    @ResponseStatus(HttpStatus.CREATED)
    ReviewImageUploadResponse issueUpload(
            @AuthenticationPrincipal LoginMember loginMember, @Valid @RequestBody ReviewImageUploadRequest request) {
        return ReviewImageUploadResponse.from(
                reviewImageUploadService.issue(loginMember.memberId(), request.contentType(), request.sizeBytes()));
    }
}
