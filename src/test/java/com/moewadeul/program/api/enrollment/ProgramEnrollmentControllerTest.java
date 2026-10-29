package com.moewadeul.program.api.enrollment;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.idempotency.application.IdempotencyExecutor;
import com.moewadeul.idempotency.domain.IdempotencyRecordRepository;
import com.moewadeul.program.application.enrollment.ProgramApplyResult;
import com.moewadeul.program.application.enrollment.ProgramApplyService;
import com.moewadeul.program.application.enrollment.ProgramVacancyAlertService;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramErrorCode;
import com.moewadeul.trust.domain.TrustErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProgramEnrollmentController.class)
@Import({
    GlobalExceptionHandler.class,
    TraceIdFilter.class,
    SecurityConfig.class,
    IdempotencyExecutor.class,
    ProgramEnrollmentControllerTest.ClockConfig.class
})
class ProgramEnrollmentControllerTest {

    private static final Instant AT = Instant.parse("2026-11-01T01:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProgramApplyService programApplyService;

    @MockitoBean
    private ProgramVacancyAlertService programVacancyAlertService;

    @MockitoBean
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    @DisplayName("[F-18] 인증 회원이 신청하면 201과 신청 ID, 상태, 결제 기한, 금액을 응답한다")
    void applyReturnsCreated() {
        when(idempotencyRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(programApplyService.apply(4L, 9L))
                .thenReturn(new ProgramApplyResult(70L, ProgramApplicationStatus.PENDING_PAYMENT, AT, 30000));

        MvcTestResult result = applyRequest(9L, "key-1").with(member(4L)).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.applicationId").isEqualTo(70);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING_PAYMENT");
        assertThat(result).bodyJson().extractingPath("$.paymentDueAt").isEqualTo("2026-11-01T01:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.amount").isEqualTo(30000);
    }

    @Test
    @DisplayName("[F-18][NFR-03] Idempotency-Key 헤더가 없으면 400 IDEMPOTENCY_KEY_REQUIRED이고 서비스를 부르지 않는다")
    void applyRequiresIdempotencyKey() {
        MvcTestResult result = mvc.post()
                .uri("/api/programs/9/applications")
                .with(csrf())
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        verifyNoInteractions(programApplyService);
    }

    @Test
    @DisplayName("[F-18] 로그인하지 않고 신청하면 401 AUTHENTICATION_REQUIRED이다")
    void applyRequiresLogin() {
        MvcTestResult result = mvc.post()
                .uri("/api/programs/9/applications")
                .header("Idempotency-Key", "key-1")
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[F-18][TR-03] 이메일 인증 전의 회원이 신청하면 403 MEMBER_NOT_VERIFIED이다")
    void applyRejectsUnverifiedMember() {
        LoginMember unverified = new LoginMember(4L, "USER", false);
        RequestPostProcessor principal = authentication(UsernamePasswordAuthenticationToken.authenticated(
                unverified, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        MvcTestResult result = applyRequest(9L, "key-1").with(principal).exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(programApplyService);
    }

    @Test
    @DisplayName("[F-18] CSRF 토큰 없이 신청하면 403이다")
    void applyRequiresCsrf() {
        MvcTestResult result = mvc.post()
                .uri("/api/programs/9/applications")
                .header("Idempotency-Key", "key-1")
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(programApplyService);
    }

    @Test
    @DisplayName("[F-18] 서비스가 던진 오류 코드를 상태 코드와 함께 응답한다")
    void applyMapsServiceErrors() {
        when(idempotencyRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(programApplyService.apply(4L, 1L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(programApplyService.apply(4L, 2L))
                .thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_NOT_IN_APPLY_PERIOD));
        when(programApplyService.apply(4L, 3L)).thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_SOLD_OUT));
        when(programApplyService.apply(4L, 4L))
                .thenThrow(new BusinessException(TrustErrorCode.TRUST_LEVEL_INSUFFICIENT));
        when(programApplyService.apply(4L, 5L))
                .thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_ALREADY_APPLIED));

        assertError(1L, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertError(2L, HttpStatus.BAD_REQUEST, "PROGRAM_NOT_IN_APPLY_PERIOD");
        assertError(3L, HttpStatus.CONFLICT, "PROGRAM_SOLD_OUT");
        assertError(4L, HttpStatus.FORBIDDEN, "TRUST_LEVEL_INSUFFICIENT");
        assertError(5L, HttpStatus.CONFLICT, "PROGRAM_ALREADY_APPLIED");
    }

    @Test
    @DisplayName("[F-18][PG-07] 자리가 없어 409이면 응답 메시지로 빈자리 알림 신청을 안내한다")
    void soldOutMessageGuidesVacancyAlert() {
        when(idempotencyRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(programApplyService.apply(4L, 3L)).thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_SOLD_OUT));

        MvcTestResult result = applyRequest(3L, "key-3").with(member(4L)).exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.message").asString().contains("빈자리 알림");
    }

    @Test
    @DisplayName("[F-20][PG-07] 빈자리 알림을 새로 신청하면 201, 이미 신청한 상태이면 200이고 본문은 없다")
    void subscribeVacancyAlertReturnsCreatedOrOk() {
        when(programVacancyAlertService.subscribe(4L, 9L)).thenReturn(true);
        when(programVacancyAlertService.subscribe(4L, 10L)).thenReturn(false);

        MvcTestResult created = vacancyAlertPost(9L).with(member(4L)).exchange();
        MvcTestResult existing = vacancyAlertPost(10L).with(member(4L)).exchange();

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).body().isEmpty();
        assertThat(existing).hasStatus(HttpStatus.OK);
        assertThat(existing).body().isEmpty();
    }

    @Test
    @DisplayName("[F-20][PG-07] 빈자리 알림을 해제하면 204이다")
    void unsubscribeVacancyAlertReturnsNoContent() {
        MvcTestResult result = mvc.delete()
                .uri("/api/programs/9/vacancy-alerts")
                .with(csrf())
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(programVacancyAlertService).unsubscribe(4L, 9L);
    }

    @Test
    @DisplayName("[F-20][PG-07] 로그인하지 않고 빈자리 알림을 신청하거나 해제하면 401 AUTHENTICATION_REQUIRED이다")
    void vacancyAlertRequiresLogin() {
        MvcTestResult subscribe = vacancyAlertPost(9L).exchange();
        MvcTestResult unsubscribe =
                mvc.delete().uri("/api/programs/9/vacancy-alerts").with(csrf()).exchange();

        assertThat(subscribe).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(subscribe).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(unsubscribe).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(programVacancyAlertService);
    }

    @Test
    @DisplayName("[F-20][PG-07] 서비스가 던진 NOT_FOUND, PROGRAM_INVALID_STATE, PROGRAM_SEATS_AVAILABLE를 404와 409로 응답한다")
    void subscribeVacancyAlertMapsServiceErrors() {
        when(programVacancyAlertService.subscribe(4L, 1L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(programVacancyAlertService.subscribe(4L, 2L))
                .thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE));
        when(programVacancyAlertService.subscribe(4L, 3L))
                .thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_SEATS_AVAILABLE));

        MvcTestResult notFound = vacancyAlertPost(1L).with(member(4L)).exchange();
        MvcTestResult invalidState = vacancyAlertPost(2L).with(member(4L)).exchange();
        MvcTestResult seatsAvailable = vacancyAlertPost(3L).with(member(4L)).exchange();

        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(invalidState).hasStatus(HttpStatus.CONFLICT);
        assertThat(invalidState).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_INVALID_STATE");
        assertThat(seatsAvailable).hasStatus(HttpStatus.CONFLICT);
        assertThat(seatsAvailable).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_SEATS_AVAILABLE");
    }

    private MockMvcTester.MockMvcRequestBuilder vacancyAlertPost(long programId) {
        return mvc.post().uri("/api/programs/" + programId + "/vacancy-alerts").with(csrf());
    }

    private void assertError(long programId, HttpStatus status, String code) {
        MvcTestResult result =
                applyRequest(programId, "key-" + programId).with(member(4L)).exchange();

        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private MockMvcTester.MockMvcRequestBuilder applyRequest(long programId, String key) {
        return mvc.post()
                .uri("/api/programs/" + programId + "/applications")
                .header("Idempotency-Key", key)
                .with(csrf());
    }

    private static RequestPostProcessor member(long memberId) {
        LoginMember loginMember = new LoginMember(memberId, "USER", true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        Clock clock() {
            return MutableClock.atDefaultInstant();
        }
    }
}
