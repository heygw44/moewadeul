package com.moewadeul.basecamp.api.lifecycle;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.basecamp.application.lifecycle.BasecampEditService;
import com.moewadeul.basecamp.application.lifecycle.BasecampOpenCommand;
import com.moewadeul.basecamp.application.lifecycle.BasecampOpenCommand.JoinConditionCommand;
import com.moewadeul.basecamp.application.lifecycle.BasecampOpenResult;
import com.moewadeul.basecamp.application.lifecycle.BasecampOpenService;
import com.moewadeul.basecamp.application.lifecycle.BasecampReviseCommand;
import com.moewadeul.basecamp.application.lifecycle.BasecampStatusResult;
import com.moewadeul.basecamp.application.lifecycle.BasecampTransitionService;
import com.moewadeul.basecamp.domain.BasecampErrorCode;
import com.moewadeul.basecamp.domain.BasecampException;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.idempotency.IdempotencyExecutor;
import com.moewadeul.common.idempotency.IdempotencyRecordJpaRepository;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.trace.TraceIdFilter;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(BasecampLifecycleController.class)
@Import({
    GlobalExceptionHandler.class,
    TraceIdFilter.class,
    SecurityConfig.class,
    IdempotencyExecutor.class,
    BasecampLifecycleControllerTest.ClockConfig.class
})
class BasecampLifecycleControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String BASECAMPS = "/api/basecamps";
    private static final String BASECAMP = "/api/basecamps/77";
    private static final String CONTACT_URL = "https://open.kakao.com/o/example";
    private static final String VALID_BODY = body("\"title\":\"북한산 백패킹\"", "\"capacity\":4");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BasecampOpenService basecampOpenService;

    @MockitoBean
    private BasecampTransitionService basecampTransitionService;

    @MockitoBean
    private BasecampEditService basecampEditService;

    @MockitoBean
    private IdempotencyRecordJpaRepository idempotencyRecordRepository;

    @Test
    @DisplayName("[F-12] 베이스캠프를 열면 201과 basecampId·status를 응답하고, 요청 값과 로그인한 회원의 ID를 서비스에 넘긴다")
    void openReturnsCreated() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(55L, "RECRUITING"));

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 55, \"status\": \"RECRUITING\" }");
        ArgumentCaptor<BasecampOpenCommand> captor = ArgumentCaptor.forClass(BasecampOpenCommand.class);
        verify(basecampOpenService).open(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BasecampOpenCommand(
                        101L, "북한산 백패킹", "함께 가요", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), 4, null));
    }

    @Test
    @DisplayName("[F-12][BC-05] 합류 조건을 보내면 그대로 서비스에 넘긴다")
    void joinConditionIsPassedToService() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(55L, "RECRUITING"));
        String joinCondition =
                "\"joinCondition\":{\"minTrustLevel\":2,\"ageGroupMin\":20,\"ageGroupMax\":30,\"sameGenderOnly\":true}";

        // when
        MvcTestResult result = post(verified(), body("\"title\":\"북한산 백패킹\"", "\"capacity\":4", joinCondition));

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        ArgumentCaptor<BasecampOpenCommand> captor = ArgumentCaptor.forClass(BasecampOpenCommand.class);
        verify(basecampOpenService).open(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().joinCondition()).isEqualTo(new JoinConditionCommand(2, 20, 30, true));
    }

    @Test
    @DisplayName("[F-12] 제목이 100자이면 받고, 101자이면 title 필드 오류로 400 INVALID_INPUT을 응답한다")
    void titleLengthBoundary() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(1L, "RECRUITING"));

        // when
        MvcTestResult atLimit = post(verified(), body("\"title\":\"" + "가".repeat(100) + "\"", "\"capacity\":4"));
        MvcTestResult overLimit = post(verified(), body("\"title\":\"" + "가".repeat(101) + "\"", "\"capacity\":4"));

        // then
        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(overLimit).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(overLimit).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(overLimit)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='title')]")
                .asList()
                .hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesWithMissingRequiredValue")
    @DisplayName("[F-12] 필수 값이 없거나 비어 있으면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void openRejectsMissingRequiredValue(String description, String requestBody) {
        // when
        MvcTestResult result = post(verified(), requestBody);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(basecampOpenService);
    }

    static Stream<Arguments> bodiesWithMissingRequiredValue() {
        return Stream.of(
                Arguments.of(
                        "장소 없음",
                        "{" + String.join(",", "\"title\":\"가\"", "\"description\":\"나\"", dates(), "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "제목 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"description\":\"나\"", dates(), "\"capacity\":4")
                                + "}"),
                Arguments.of("제목 공백", body("\"title\":\"   \"", "\"capacity\":4")),
                Arguments.of(
                        "설명 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"title\":\"가\"", dates(), "\"capacity\":4") + "}"),
                Arguments.of(
                        "출발일 없음",
                        "{"
                                + String.join(
                                        ",",
                                        "\"spotId\":101",
                                        "\"title\":\"가\"",
                                        "\"description\":\"나\"",
                                        "\"endDate\":\"2026-10-22\"",
                                        "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "종료일 없음",
                        "{"
                                + String.join(
                                        ",",
                                        "\"spotId\":101",
                                        "\"title\":\"가\"",
                                        "\"description\":\"나\"",
                                        "\"startDate\":\"2026-10-20\"",
                                        "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "정원 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"title\":\"가\"", "\"description\":\"나\"", dates())
                                + "}"));
    }

    @Test
    @DisplayName("[F-12] 로그인하지 않은 사용자가 열면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousIsUnauthorized() {
        // when
        MvcTestResult result = mvc.post()
                .uri(BASECAMPS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(basecampOpenService);
    }

    @Test
    @DisplayName("[F-12][TR-03] 이메일 인증 전의 회원이 열면 403 MEMBER_NOT_VERIFIED를 응답한다")
    void unverifiedMemberIsForbidden() {
        // when
        MvcTestResult result = post(unverified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampOpenService);
    }

    @Test
    @DisplayName("[F-12] CSRF 토큰 없이 열면 403을 응답하고 서비스를 부르지 않는다")
    void missingCsrfIsForbidden() {
        // when
        MvcTestResult result = mvc.post()
                .uri(BASECAMPS)
                .with(verified())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(basecampOpenService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceErrors")
    @DisplayName("[F-12] 서비스가 던진 오류는 코드와 상태 그대로 응답한다")
    void serviceErrorsAreMapped(BusinessException error, HttpStatus status) {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenThrow(error);

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(status);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo(error.getErrorCode().name());
    }

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT), HttpStatus.FORBIDDEN),
                Arguments.of(new BusinessException(CommonErrorCode.NOT_FOUND), HttpStatus.NOT_FOUND),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_OPEN_LIMIT), HttpStatus.BAD_REQUEST),
                Arguments.of(
                        new BasecampException(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID), HttpStatus.BAD_REQUEST),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_WARNING_SPOT), HttpStatus.BAD_REQUEST),
                Arguments.of(
                        new BasecampException(BasecampErrorCode.BASECAMP_CAPACITY_INVALID), HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("[F-14][BC-12] 마감·재개·확정·취소는 200과 basecampId·status를 응답하고, 경로의 ID와 로그인한 회원 ID를 서비스에 넘긴다")
    void transitionsReturnStatus() {
        // given
        when(basecampTransitionService.close(77L, MEMBER_ID)).thenReturn(new BasecampStatusResult(77L, "CLOSED"));
        when(basecampTransitionService.reopen(77L, MEMBER_ID)).thenReturn(new BasecampStatusResult(77L, "RECRUITING"));
        when(basecampTransitionService.confirm(77L, MEMBER_ID)).thenReturn(new BasecampStatusResult(77L, "CONFIRMED"));
        when(basecampTransitionService.cancel(77L, MEMBER_ID)).thenReturn(new BasecampStatusResult(77L, "CANCELED"));

        // when
        MvcTestResult close = send("POST", BASECAMP + "/close", verified(), true, null);
        MvcTestResult reopen = send("POST", BASECAMP + "/reopen", verified(), true, null);
        MvcTestResult confirm = send("POST", BASECAMP + "/confirm", verified(), true, null);
        MvcTestResult cancel = send("POST", BASECAMP + "/cancel", verified(), true, null);

        // then
        assertThat(close).hasStatus(HttpStatus.OK);
        assertThat(close).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 77, \"status\": \"CLOSED\" }");
        assertThat(reopen).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 77, \"status\": \"RECRUITING\" }");
        assertThat(confirm).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 77, \"status\": \"CONFIRMED\" }");
        assertThat(cancel).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 77, \"status\": \"CANCELED\" }");
    }

    @Test
    @DisplayName("[F-14] 연락 수단을 등록하면 200과 basecampId·status를 응답하고, 연락 수단을 서비스에 넘긴다")
    void registerContactReturnsStatus() {
        // given
        when(basecampEditService.registerContact(77L, MEMBER_ID, CONTACT_URL))
                .thenReturn(new BasecampStatusResult(77L, "CONFIRMED"));

        // when
        MvcTestResult result = send("PUT", BASECAMP + "/contact", verified(), true, contactBody(CONTACT_URL));

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 77, \"status\": \"CONFIRMED\" }");
    }

    @Test
    @DisplayName("[F-14] 수정하면 200과 basecampId·status를 응답하고, 보낸 필드만 서비스에 넘긴다")
    void reviseReturnsStatus() {
        // given
        when(basecampEditService.revise(eq(77L), eq(MEMBER_ID), any()))
                .thenReturn(new BasecampStatusResult(77L, "RECRUITING"));

        // when
        MvcTestResult result = send("PATCH", BASECAMP, verified(), true, "{\"capacity\":5}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 77, \"status\": \"RECRUITING\" }");
        ArgumentCaptor<BasecampReviseCommand> captor = ArgumentCaptor.forClass(BasecampReviseCommand.class);
        verify(basecampEditService).revise(eq(77L), eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new BasecampReviseCommand(null, null, null, 5));
    }

    @Test
    @DisplayName("[F-14] 로그인하지 않은 사용자는 수동 전이·연락 수단·수정이 401이고, 이메일 인증 전의 회원은 403 MEMBER_NOT_VERIFIED다")
    void manualEndpointsRequireVerifiedLogin() {
        for (String[] endpoint : manualEndpoints()) {
            // when
            MvcTestResult anonymous = sendAnonymous(endpoint[0], endpoint[1], endpoint[2]);
            MvcTestResult unverified = send(endpoint[0], endpoint[1], unverified(), true, endpoint[2]);

            // then
            assertThat(anonymous).as(endpoint[1]).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(unverified).as(endpoint[1]).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(unverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        }
        verifyNoInteractions(basecampTransitionService, basecampEditService);
    }

    @Test
    @DisplayName("[F-14] CSRF 토큰 없이 수동 전이·연락 수단·수정을 요청하면 403이다")
    void manualEndpointsRequireCsrf() {
        for (String[] endpoint : manualEndpoints()) {
            // when
            MvcTestResult result = send(endpoint[0], endpoint[1], verified(), false, endpoint[2]);

            // then
            assertThat(result).as(endpoint[1]).hasStatus(HttpStatus.FORBIDDEN);
        }
        verifyNoInteractions(basecampTransitionService, basecampEditService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContactBodies")
    @DisplayName("[F-14] 연락 수단이 없거나 공백이거나 255자를 넘거나 https URL이 아니면 400 INVALID_INPUT이고 fieldErrors가 있다")
    void registerContactRejectsInvalidContact(String body) {
        // when
        MvcTestResult result = send("PUT", BASECAMP + "/contact", verified(), true, body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").isNotNull();
        verifyNoInteractions(basecampEditService);
    }

    static Stream<String> invalidContactBodies() {
        return Stream.of(
                "{}",
                contactBody("   "),
                contactBody("http://open.kakao.com/o/example"),
                contactBody("javascript:alert(1)"),
                contactBody("https://"),
                contactBody("kakao open chat"),
                contactBody("https://example.com/" + "a".repeat(240)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{}",
                "{\"title\":null}",
                "{\"title\":\"   \"}",
                "{\"description\":\"\"}",
                "{\"title\":\"가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하\"}"
            })
    @DisplayName("[F-14] 수정할 필드를 하나도 보내지 않았거나 제목·설명이 비었거나 너무 길면 400 INVALID_INPUT이고 fieldErrors가 있다")
    void reviseRejectsInvalidBody(String body) {
        // when
        MvcTestResult result = send("PATCH", BASECAMP, verified(), true, body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").isNotNull();
        verifyNoInteractions(basecampEditService);
    }

    @Test
    @DisplayName("[F-14] 수동 전이·수정 서비스가 던진 오류는 코드와 상태 그대로 응답한다")
    void manualErrorsAreMapped() {
        // given
        when(basecampTransitionService.close(77L, MEMBER_ID))
                .thenThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));
        when(basecampTransitionService.confirm(77L, MEMBER_ID))
                .thenThrow(new BasecampException(BasecampErrorCode.BASECAMP_NOT_ENOUGH_MEMBERS));
        when(basecampEditService.revise(eq(77L), eq(MEMBER_ID), any()))
                .thenThrow(new BasecampException(BasecampErrorCode.BASECAMP_CAPACITY_INVALID));

        // when
        MvcTestResult denied = send("POST", BASECAMP + "/close", verified(), true, null);
        MvcTestResult notEnough = send("POST", BASECAMP + "/confirm", verified(), true, null);
        MvcTestResult capacity = send("PATCH", BASECAMP, verified(), true, "{\"capacity\":3}");

        // then
        assertThat(denied).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(notEnough).hasStatus(HttpStatus.CONFLICT);
        assertThat(notEnough).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_NOT_ENOUGH_MEMBERS");
        assertThat(capacity).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(capacity).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_CAPACITY_INVALID");
    }

    private static List<String[]> manualEndpoints() {
        return List.of(
                new String[] {"POST", BASECAMP + "/close", null},
                new String[] {"POST", BASECAMP + "/reopen", null},
                new String[] {"POST", BASECAMP + "/confirm", null},
                new String[] {"POST", BASECAMP + "/cancel", null},
                new String[] {"PUT", BASECAMP + "/contact", contactBody(CONTACT_URL)},
                new String[] {"PATCH", BASECAMP, "{\"capacity\":5}"});
    }

    private static String contactBody(String contactInfo) {
        return "{\"contactInfo\":\"%s\"}".formatted(contactInfo);
    }

    private MvcTestResult sendAnonymous(String method, String uri, String requestBody) {
        return send(method, uri, null, true, requestBody);
    }

    private MvcTestResult send(
            String method, String uri, RequestPostProcessor login, boolean withCsrf, String requestBody) {
        MockMvcTester.MockMvcRequestBuilder builder = switch (method) {
            case "PUT" -> mvc.put().uri(uri);
            case "PATCH" -> mvc.patch().uri(uri);
            default -> mvc.post().uri(uri);
        };
        if (login != null) {
            builder = builder.with(login);
        }
        if (withCsrf) {
            builder = builder.with(csrf());
        }
        if (requestBody != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(requestBody);
        }
        return builder.exchange();
    }

    private static String dates() {
        return "\"startDate\":\"2026-10-20\",\"endDate\":\"2026-10-22\"";
    }

    private static String body(String... overrides) {
        return "{\"spotId\":101,\"description\":\"함께 가요\"," + dates() + "," + String.join(",", overrides) + "}";
    }

    @Test
    @DisplayName("[F-12][NFR-03] Idempotency-Key 헤더가 없으면 400 IDEMPOTENCY_KEY_REQUIRED이고 서비스를 부르지 않는다")
    void openRequiresIdempotencyKey() {
        // when
        MvcTestResult result = mvc.post()
                .uri(BASECAMPS)
                .with(verified())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(basecampOpenService);
    }

    private MvcTestResult post(RequestPostProcessor login, String requestBody) {
        return mvc.post()
                .uri(BASECAMPS)
                .with(login)
                .with(csrf())
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();
    }

    private static RequestPostProcessor verified() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }

    private static RequestPostProcessor unverified() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        Clock clock() {
            return MutableClock.atDefaultInstant();
        }
    }
}
