package com.moewadeul.member.api.signup;

import com.moewadeul.common.security.LoginMember;
import com.moewadeul.member.application.signup.AdminDisposableEmailDomainService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/disposable-email-domains")
class DisposableEmailDomainAdminController {

    private final AdminDisposableEmailDomainService service;

    DisposableEmailDomainAdminController(AdminDisposableEmailDomainService service) {
        this.service = service;
    }

    @Operation(
            summary = "관리자 차단 도메인 목록",
            description = "관리자가 가입 차단 목록에 더한 일회용 이메일 도메인을 최근에 더한 순서로 모두 돌려준다. 공개 차단 목록은 담지 않는다.")
    @GetMapping
    List<DisposableEmailDomainAdminResponse> list() {
        return service.list().stream()
                .map(DisposableEmailDomainAdminResponse::from)
                .toList();
    }

    @Operation(
            summary = "관리자 차단 도메인 추가",
            description = "도메인을 소문자로 바꿔 가입 차단 목록에 더하고 201과 함께 돌려준다. 그 도메인과 하위 도메인의 이메일로는 가입할 수 없다. "
                    + "도메인 형식(점으로 나눈 영문·숫자·'-', 253자 이하)이 아니면 400 INVALID_INPUT, "
                    + "공개 목록이나 관리자 목록에 이미 있으면 409 MEMBER_DISPOSABLE_DOMAIN_ALREADY_BLOCKED이다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DisposableEmailDomainAdminResponse add(
            @Valid @RequestBody DisposableEmailDomainAdminRequest request, @AuthenticationPrincipal LoginMember admin) {
        return DisposableEmailDomainAdminResponse.from(service.add(admin.memberId(), request.domain()));
    }

    @Operation(
            summary = "관리자 차단 도메인 삭제",
            description = "관리자가 더한 도메인을 차단 목록에서 빼고 204를 돌려준다. 그 뒤로는 그 도메인으로 다시 가입할 수 있다. 없는 id이면 404 NOT_FOUND이다.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable long id, @AuthenticationPrincipal LoginMember admin) {
        service.remove(admin.memberId(), id);
    }
}
