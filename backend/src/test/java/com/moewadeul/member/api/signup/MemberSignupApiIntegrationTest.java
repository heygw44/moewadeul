package com.moewadeul.member.api.signup;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.member.domain.signup.DisposableEmailDomain;
import com.moewadeul.member.domain.signup.DisposableEmailDomainSource;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.member.infra.signup.DisposableEmailDomainJpaRepository;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class MemberSignupApiIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass-1!";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private DisposableEmailDomainJpaRepository disposableRepository;

    @Test
    @DisplayName("[F-01] 가입하면 201을 응답하고 UNVERIFIED 회원이 소문자 이메일과 BCrypt 해시로 저장된다")
    void signUpPersistsUnverifiedMember() {
        String email = "Mixed." + TestSequence.next() + "@Example.COM";

        MvcTestResult result = post(email, VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UNVERIFIED");
        Member member = memberRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(email.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.UNVERIFIED);
        assertThat(member.getPasswordHash()).isNotEqualTo(VALID_PASSWORD).startsWith("$2");
    }

    @Test
    @DisplayName("[F-01][EV-06] 대소문자만 다른 같은 이메일로 다시 가입하면 409를 응답한다")
    void duplicatedEmailIgnoringCaseReturnsConflict() {
        String email = "dup." + TestSequence.next() + "@example.com";
        post(email, VALID_PASSWORD, TestSequence.nickname());

        MvcTestResult result = post(email.toUpperCase(Locale.ROOT), VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_EMAIL_DUPLICATED");
    }

    @Test
    @DisplayName("[F-01][EV-04] 일회용 이메일 도메인으로는 가입할 수 없다")
    void disposableEmailReturnsBadRequest() {
        disposableRepository.save(DisposableEmailDomain.of(
                "mailinator.com", DisposableEmailDomainSource.PUBLIC, MutableClock.DEFAULT_INSTANT));

        MvcTestResult result = post("x@mailinator.com", VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_DISPOSABLE_EMAIL");
    }

    @Test
    @DisplayName("[F-01][PW-01] 약한 비밀번호면 400을 응답한다")
    void weakPasswordReturnsBadRequest() {
        MvcTestResult result = post(TestSequence.email(), "weak", TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_PASSWORD_POLICY");
    }

    @Test
    @DisplayName("[F-01] 만 14세 확인 없이 가입하면 400 ageConfirmed 오류를 응답하고 회원을 만들지 않는다")
    void signUpWithoutAgeConfirmedIsRejected() {
        String email = TestSequence.email();

        MvcTestResult result = mvc.post()
                .uri("/api/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"passwordConfirm\":\"%s\",\"nickname\":\"%s\"}"
                        .formatted(email, VALID_PASSWORD, VALID_PASSWORD, TestSequence.nickname()))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("ageConfirmed");
        assertThat(memberRepository.findAll()).noneMatch(m -> m.getEmail().equals(email));
    }

    @Test
    @DisplayName("[F-01] 닉네임이 중복이면 409를 응답한다")
    void duplicatedNicknameReturnsConflict() {
        String nickname = TestSequence.nickname();
        post(TestSequence.email(), VALID_PASSWORD, nickname);

        MvcTestResult result = post(TestSequence.email(), VALID_PASSWORD, nickname);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NICKNAME_DUPLICATED");
    }

    @Test
    @DisplayName("[F-01] 성공 응답에는 이메일·비밀번호·해시가 없다")
    void successResponseHasNoSensitiveFields() {
        MvcTestResult result = post(TestSequence.email(), VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().doesNotHavePath("$.email");
        assertThat(result).bodyJson().doesNotHavePath("$.password");
        assertThat(result).bodyJson().doesNotHavePath("$.passwordHash");
    }

    @Test
    @DisplayName("[F-01] 잘못된 입력의 오류 응답에는 traceId가 있다")
    void invalidInputResponseHasTraceId() {
        MvcTestResult result = post("not-an-email", VALID_PASSWORD, TestSequence.nickname());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
    }

    @Test
    @DisplayName("[F-01][EV-06] ASCII가 아닌 글자가 든 이메일로 가입하면 400 INVALID_INPUT이고 fieldErrors에 email이 있으며 회원이 만들어지지 않는다")
    void nonAsciiEmailReturnsBadRequest() {
        MvcTestResult fullWidth =
                post("ｖictim" + TestSequence.next() + "@example.com", VALID_PASSWORD, TestSequence.nickname());
        MvcTestResult invisible =
                post("vic\\u200Btim" + TestSequence.next() + "@example.com", VALID_PASSWORD, TestSequence.nickname());

        assertThat(fullWidth).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(fullWidth).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(fullWidth)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].field")
                .isEqualTo("email");
        assertThat(invisible).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(invisible)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].field")
                .isEqualTo("email");
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP에서 가입이 20건 있으면 21번째는 429 SIGNUP_LIMITED이고 Retry-After가 있다")
    void twentyFirstSignupFromSameIpIsLimited() {
        for (int signedUp = 0; signedUp < 20; signedUp++) {
            assertThat(post(TestSequence.email(), VALID_PASSWORD, TestSequence.nickname()))
                    .hasStatus(HttpStatus.CREATED);
        }

        MvcTestResult limited = post(TestSequence.email(), VALID_PASSWORD, TestSequence.nickname());

        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("SIGNUP_LIMITED");
        assertThat(Long.parseLong(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                .isBetween(1L, Duration.ofHours(1).toSeconds());
        assertThat(memberRepository.count()).isEqualTo(20);
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP에서 이미 쓰인 이메일로 가입을 20번 시도하면 21번째 같은 요청은 409가 아니라 429 SIGNUP_LIMITED다")
    void twentyFirstDuplicateEmailSignupFromSameIpIsLimited() {
        String taken = TestSequence.email();
        assertThat(post(taken, VALID_PASSWORD, TestSequence.nickname())).hasStatus(HttpStatus.CREATED);
        for (int attempted = 0; attempted < 19; attempted++) {
            assertThat(post(taken, VALID_PASSWORD, TestSequence.nickname())).hasStatus(HttpStatus.CONFLICT);
        }

        MvcTestResult limited = post(taken, VALID_PASSWORD, TestSequence.nickname());

        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("SIGNUP_LIMITED");
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    private MvcTestResult post(String email, String password, String nickname) {
        String body =
                "{\"email\":\"%s\",\"password\":\"%s\",\"passwordConfirm\":\"%s\",\"nickname\":\"%s\",\"ageConfirmed\":true}"
                        .formatted(email, password, password, nickname);
        return mvc.post()
                .uri("/api/members")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
