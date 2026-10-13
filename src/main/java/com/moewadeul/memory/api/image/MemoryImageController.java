package com.moewadeul.memory.api.image;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.memory.application.image.MemoryImageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MemoryImageController {

    private final MemoryImageUploadService memoryImageUploadService;

    MemoryImageController(MemoryImageUploadService memoryImageUploadService) {
        this.memoryImageUploadService = memoryImageUploadService;
    }

    @Operation(
            summary = "추억 기록 사진 업로드 URL 발급",
            description = "이메일 인증을 마친 회원이 추억 기록에 붙일 사진을 올릴 URL을 받는다. 서버는 파일을 받지 않는다. "
                    + "contentType은 image/jpeg, image/png, image/webp 중 하나이고, sizeBytes는 1~5242880(5MB)이다. 어기면 400 INVALID_INPUT이다. "
                    + "201과 함께 imageId, uploadUrl, method(PUT), headers(Content-Type, Content-Length), expiresAt을 준다. "
                    + "화면은 uploadUrl로 파일을 PUT하면서 headers를 그대로 보낸다. 서명에 형식과 크기가 들어 있어서 다르게 올리면 저장소가 거부한다. "
                    + "URL은 10분 동안 쓸 수 있다. 올린 뒤 기록 작성·수정 요청의 imageIds에 imageId를 넣는다. 기록 하나에 10장까지 붙인다. "
                    + "24시간 안에 기록에 붙이지 않은 사진은 서버가 지운다.")
    @PostMapping("/api/memories/images")
    @ResponseStatus(HttpStatus.CREATED)
    MemoryImageUploadResponse issueUpload(
            @AuthenticationPrincipal LoginMember loginMember, @Valid @RequestBody MemoryImageUploadRequest request) {
        return MemoryImageUploadResponse.from(
                memoryImageUploadService.issue(loginMember.memberId(), request.contentType(), request.sizeBytes()));
    }
}
