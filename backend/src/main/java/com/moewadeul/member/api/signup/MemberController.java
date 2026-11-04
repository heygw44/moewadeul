package com.moewadeul.member.api.signup;

import com.moewadeul.member.application.signup.MemberSignupService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
class MemberController {

    private final MemberSignupService memberSignupService;

    MemberController(MemberSignupService memberSignupService) {
        this.memberSignupService = memberSignupService;
    }

    @Operation(
            summary = "회원가입",
            description =
                    "passwordConfirm이 password와 다르면 400 INVALID_INPUT을 응답하고 fieldErrors에 passwordConfirm 항목을 담는다. "
                            + "ageConfirmed가 true가 아니어도 400 INVALID_INPUT이고 fieldErrors에 ageConfirmed 항목을 담는다. "
                            + "닉네임은 2~20자이고 앞뒤 공백, 연속 공백, 보이지 않는 문자, '탈퇴회원_'으로 시작하는 이름은 400 INVALID_INPUT이다. "
                            + "비밀번호가 10~64자가 아니거나 영문, 숫자, 특수문자를 각각 1자 이상 넣지 않았거나 공백이나 한글처럼 눈에 보이는 ASCII 글자가 아닌 문자를 쓰면 400 MEMBER_PASSWORD_POLICY이다. "
                            + "일회용 이메일은 400 MEMBER_DISPOSABLE_EMAIL, 이미 쓰는 이메일과 닉네임은 각각 409 MEMBER_EMAIL_DUPLICATED, MEMBER_NICKNAME_DUPLICATED이다. "
                            + "같은 IP의 요청이 한도를 넘으면 429 SIGNUP_LIMITED와 Retry-After 헤더로 응답한다. "
                            + "가입하면 201과 함께 회원이 이메일 인증 전(UNVERIFIED)으로 만들어지고, 서버가 인증 코드 메일을 보낸다. 로그인은 되지만 이메일 인증이 필요한 API는 인증 뒤에 쓸 수 있다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SignupResponse signUp(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        return SignupResponse.from(memberSignupService.signUp(request.toCommand(httpRequest.getRemoteAddr())));
    }
}
