package com.moewadeul.basecamp.api.membership;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.basecamp.application.membership.BasecampApplicationCancelService;
import com.moewadeul.basecamp.application.membership.BasecampApplicationListQuery;
import com.moewadeul.basecamp.application.membership.BasecampApplicationListService;
import com.moewadeul.basecamp.application.membership.BasecampApplicationPage;
import com.moewadeul.basecamp.application.membership.BasecampApplyCommand;
import com.moewadeul.basecamp.application.membership.BasecampApplyResult;
import com.moewadeul.basecamp.application.membership.BasecampApplyService;
import com.moewadeul.basecamp.application.membership.BasecampApprovalService;
import com.moewadeul.basecamp.application.membership.BasecampApproveResult;
import com.moewadeul.basecamp.application.membership.BasecampMembershipService;
import com.moewadeul.basecamp.application.membership.BasecampRejectResult;
import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import com.moewadeul.basecamp.domain.BasecampErrorCode;
import com.moewadeul.basecamp.domain.KickReason;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(BasecampMembershipController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class BasecampMembershipControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String BASECAMPS = "/api/basecamps";
    private static final String APPLICATIONS = "/api/basecamps/77/applications";
    private static final String APPROVE = "/api/basecamps/77/applications/501/approve";
    private static final String REJECT = "/api/basecamps/77/applications/501/reject";
    private static final String LEAVE = "/api/basecamps/77/members/me";
    private static final String KICK = "/api/basecamps/77/members/31/kick";
    private static final String MY_APPLICATION = "/api/basecamps/77/applications/me";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BasecampApplyService basecampApplyService;

    @MockitoBean
    private BasecampApplicationCancelService basecampApplicationCancelService;

    @MockitoBean
    private BasecampApplicationListService basecampApplicationListService;

    @MockitoBean
    private BasecampApprovalService basecampApprovalService;

    @MockitoBean
    private BasecampMembershipService basecampMembershipService;

    @Test
    @DisplayName("[F-13][BC-06] 합류를 신청하면 201과 applicationId·status를 응답하고, 경로의 베이스캠프 ID와 로그인한 회원 ID, 메시지를 서비스에 넘긴다")
    void applyReturnsCreated() {
        // given
        when(basecampApplyService.apply(any())).thenReturn(new BasecampApplyResult(901L, "PENDING"));

        // when
        MvcTestResult result = postApply(verified(), "{\"message\":\"같이 가고 싶어요\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"applicationId\": 901, \"status\": \"PENDING\" }");
        verify(basecampApplyService).apply(new BasecampApplyCommand(77L, MEMBER_ID, "같이 가고 싶어요"));
    }

    @Test
    @DisplayName("[F-13][BC-06] 요청 본문이 없거나 message가 없어도 201이고, 메시지는 null로 넘긴다")
    void applyAcceptsMissingBodyAndMessage() {
        // given
        when(basecampApplyService.apply(any())).thenReturn(new BasecampApplyResult(901L, "PENDING"));

        // when
        MvcTestResult withoutBody =
                mvc.post().uri(APPLICATIONS).with(verified()).with(csrf()).exchange();
        MvcTestResult withoutMessage = postApply(verified(), "{}");

        // then
        assertThat(withoutBody).hasStatus(HttpStatus.CREATED);
        assertThat(withoutMessage).hasStatus(HttpStatus.CREATED);
        verify(basecampApplyService, times(2)).apply(new BasecampApplyCommand(77L, MEMBER_ID, null));
    }

    @Test
    @DisplayName("[F-13] 신청 메시지가 500자이면 받고, 501자이면 message 필드 오류로 400 INVALID_INPUT을 응답한다")
    void applyMessageLengthBoundary() {
        // given
        when(basecampApplyService.apply(any())).thenReturn(new BasecampApplyResult(901L, "PENDING"));

        // when
        MvcTestResult atLimit = postApply(verified(), "{\"message\":\"" + "가".repeat(500) + "\"}");
        MvcTestResult overLimit = postApply(verified(), "{\"message\":\"" + "가".repeat(501) + "\"}");

        // then
        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(overLimit).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(overLimit).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(overLimit)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].field")
                .isEqualTo("message");
        verify(basecampApplyService).apply(any());
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않은 사용자가 신청하면 401, 이메일 인증 전의 회원이 신청하면 403 MEMBER_NOT_VERIFIED이고 서비스를 부르지 않는다")
    void applyRequiresVerifiedLogin() {
        // when
        MvcTestResult anonymous = mvc.post()
                .uri(APPLICATIONS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
        MvcTestResult unverifiedMember = postApply(unverified(), "{}");

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(unverifiedMember).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverifiedMember).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampApplyService);
    }

    @Test
    @DisplayName("[F-13] CSRF 토큰 없이 신청하거나 취소하면 403을 응답하고 서비스를 부르지 않는다")
    void applyAndCancelRequireCsrf() {
        // when
        MvcTestResult apply = mvc.post()
                .uri(APPLICATIONS)
                .with(verified())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
        MvcTestResult cancel = mvc.delete().uri(MY_APPLICATION).with(verified()).exchange();

        // then
        assertThat(apply).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(cancel).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(basecampApplyService, basecampApplicationCancelService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("applyErrors")
    @DisplayName("[F-13] 신청 서비스가 던진 오류는 코드와 상태 그대로 응답한다")
    void applyErrorsAreMapped(BusinessException error, HttpStatus status) {
        // given
        when(basecampApplyService.apply(any())).thenThrow(error);

        // when
        MvcTestResult result = postApply(verified(), "{}");

        // then
        assertThat(result).hasStatus(status);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo(error.getErrorCode().name());
    }

    static Stream<Arguments> applyErrors() {
        return Stream.of(
                Arguments.of(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT), HttpStatus.FORBIDDEN),
                Arguments.of(new BusinessException(CommonErrorCode.NOT_FOUND), HttpStatus.NOT_FOUND),
                Arguments.of(new BusinessException(BasecampErrorCode.BASECAMP_INVALID_STATE), HttpStatus.CONFLICT),
                Arguments.of(new BusinessException(BasecampErrorCode.BASECAMP_ALREADY_APPLIED), HttpStatus.CONFLICT),
                Arguments.of(
                        new BusinessException(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED), HttpStatus.CONFLICT),
                Arguments.of(new BusinessException(BasecampErrorCode.BASECAMP_CONDITION_NOT_MET), HttpStatus.FORBIDDEN),
                Arguments.of(new BusinessException(BasecampErrorCode.BASECAMP_DATE_CONFLICT), HttpStatus.CONFLICT),
                Arguments.of(new BusinessException(BasecampErrorCode.BASECAMP_PENDING_LIMIT), HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("[F-13][BC-06] 신청을 취소하면 204이고, 경로의 베이스캠프 ID와 로그인한 회원 ID를 서비스에 넘긴다")
    void cancelApplicationReturnsNoContent() {
        // when
        MvcTestResult result =
                mvc.delete().uri(MY_APPLICATION).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(basecampApplicationCancelService).cancel(77L, MEMBER_ID);
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않은 사용자가 취소하면 401, 이메일 인증 전의 회원이 취소하면 403 MEMBER_NOT_VERIFIED다")
    void cancelRequiresVerifiedLogin() {
        // when
        MvcTestResult anonymous = mvc.delete().uri(MY_APPLICATION).with(csrf()).exchange();
        MvcTestResult unverifiedMember =
                mvc.delete().uri(MY_APPLICATION).with(unverified()).with(csrf()).exchange();

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unverifiedMember).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverifiedMember).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampApplicationCancelService);
    }

    @Test
    @DisplayName("[F-13] 취소 서비스가 던진 NOT_FOUND와 BASECAMP_INVALID_STATE는 코드와 상태 그대로 응답한다")
    void cancelErrorsAreMapped() {
        // given
        doThrow(new BusinessException(CommonErrorCode.NOT_FOUND))
                .doThrow(new BusinessException(BasecampErrorCode.BASECAMP_INVALID_STATE))
                .when(basecampApplicationCancelService)
                .cancel(77L, MEMBER_ID);

        // when
        MvcTestResult notFound =
                mvc.delete().uri(MY_APPLICATION).with(verified()).with(csrf()).exchange();
        MvcTestResult invalidState =
                mvc.delete().uri(MY_APPLICATION).with(verified()).with(csrf()).exchange();

        // then
        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(invalidState).hasStatus(HttpStatus.CONFLICT);
        assertThat(invalidState).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_INVALID_STATE");
    }

    @Test
    @DisplayName("[F-13][BC-08] 승인하면 200이고 인원과 베이스캠프 상태를 응답하며, 경로의 ID와 로그인한 회원 ID를 서비스에 넘긴다")
    void approveReturnsHeadcountAndStatus() {
        // given
        when(basecampApprovalService.approve(77L, 501L, MEMBER_ID)).thenReturn(new BasecampApproveResult(4, "CLOSED"));

        // when
        MvcTestResult result =
                mvc.post().uri(APPROVE).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"headcount\": 4, \"status\": \"CLOSED\" }");
    }

    @Test
    @DisplayName("[F-13][BC-08] 거절하면 200이고 applicationId와 REJECTED를 응답한다")
    void rejectReturnsApplicationStatus() {
        // given
        when(basecampApprovalService.reject(77L, 501L, MEMBER_ID))
                .thenReturn(new BasecampRejectResult(501L, "REJECTED"));

        // when
        MvcTestResult result =
                mvc.post().uri(REJECT).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"applicationId\": 501, \"status\": \"REJECTED\" }");
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않은 사용자가 승인·거절·신청 목록을 부르면 401이고, 이메일 인증 전의 회원은 403 MEMBER_NOT_VERIFIED다")
    void decisionEndpointsRequireVerifiedLogin() {
        // when
        MvcTestResult approveAnonymous = mvc.post().uri(APPROVE).with(csrf()).exchange();
        MvcTestResult rejectAnonymous = mvc.post().uri(REJECT).with(csrf()).exchange();
        MvcTestResult listAnonymous = mvc.get().uri(APPLICATIONS).exchange();
        MvcTestResult approveUnverified =
                mvc.post().uri(APPROVE).with(unverified()).with(csrf()).exchange();
        MvcTestResult listUnverified =
                mvc.get().uri(APPLICATIONS).with(unverified()).exchange();

        // then
        assertThat(approveAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(rejectAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(listAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(approveUnverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(listUnverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(listUnverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampApprovalService, basecampApplicationListService);
    }

    @Test
    @DisplayName("[F-13] 승인·거절 서비스가 던진 ACCESS_DENIED, NOT_FOUND, BASECAMP_FULL은 코드와 상태 그대로 응답한다")
    void decisionErrorsAreMapped() {
        // given
        when(basecampApprovalService.approve(77L, 501L, MEMBER_ID))
                .thenThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .thenThrow(new BusinessException(BasecampErrorCode.BASECAMP_FULL));
        when(basecampApprovalService.reject(77L, 501L, MEMBER_ID))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult denied =
                mvc.post().uri(APPROVE).with(verified()).with(csrf()).exchange();
        MvcTestResult full =
                mvc.post().uri(APPROVE).with(verified()).with(csrf()).exchange();
        MvcTestResult notFound =
                mvc.post().uri(REJECT).with(verified()).with(csrf()).exchange();

        // then
        assertThat(denied).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(full).hasStatus(HttpStatus.CONFLICT);
        assertThat(full).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_FULL");
        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-13] 신청 목록의 status를 생략하면 PENDING, page는 0, size는 20으로 서비스를 부른다")
    void listApplicationsUsesDefaults() {
        // given
        when(basecampApplicationListService.list(eq(77L), eq(MEMBER_ID), any()))
                .thenReturn(new BasecampApplicationPage(List.of(), 0, 20, false));

        // when
        MvcTestResult result = mvc.get().uri(APPLICATIONS).with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .isStrictlyEqualTo("{ \"content\": [], \"page\": 0, \"size\": 20, \"hasNext\": false }");
        verify(basecampApplicationListService)
                .list(77L, MEMBER_ID, new BasecampApplicationListQuery(BasecampApplicationStatus.PENDING, 0, 20));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"status=UNKNOWN", "status=pending", "size=0", "size=51", "page=-1"})
    @DisplayName("[F-13] 신청 목록의 status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다")
    void listApplicationsRejectsInvalidParameters(String query) {
        // when
        MvcTestResult result =
                mvc.get().uri(APPLICATIONS + "?" + query).with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(basecampApplicationListService);
    }

    @Test
    @DisplayName("[F-13][BC-21] 탈퇴하면 204이고 경로의 베이스캠프 ID와 로그인한 회원 ID를 서비스에 넘긴다")
    void leaveReturnsNoContent() {
        // when
        MvcTestResult result =
                mvc.delete().uri(LEAVE).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(basecampMembershipService).leave(77L, MEMBER_ID);
    }

    @Test
    @DisplayName("[F-13][BC-22] 강퇴하면 204이고 경로의 ID, 로그인한 회원 ID, 사유를 서비스에 넘긴다")
    void kickReturnsNoContent() {
        // when
        MvcTestResult result = postKick(verified(), "{\"reason\":\"NO_CONTACT\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(basecampMembershipService).kick(77L, 31L, MEMBER_ID, KickReason.NO_CONTACT);
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않은 사용자는 탈퇴·강퇴가 401이고, 이메일 인증 전의 회원은 403 MEMBER_NOT_VERIFIED다")
    void membershipEndpointsRequireVerifiedLogin() {
        // when
        MvcTestResult leaveAnonymous = mvc.delete().uri(LEAVE).with(csrf()).exchange();
        MvcTestResult kickAnonymous = mvc.post()
                .uri(KICK)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\"}")
                .exchange();
        MvcTestResult leaveUnverified =
                mvc.delete().uri(LEAVE).with(unverified()).with(csrf()).exchange();
        MvcTestResult kickUnverified = postKick(unverified(), "{\"reason\":\"OTHER\"}");

        // then
        assertThat(leaveAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(kickAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(leaveUnverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(kickUnverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(kickUnverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampMembershipService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"UNKNOWN\"}", "{\"reason\":\"other\"}"})
    @DisplayName("[F-13][BC-22] 강퇴 사유가 없거나 정해진 값이 아니면 400 INVALID_INPUT이고 fieldErrors가 있다")
    void kickRejectsMissingOrUnknownReason(String body) {
        // when
        MvcTestResult result = postKick(verified(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").isNotNull();
        verifyNoInteractions(basecampMembershipService);
    }

    @Test
    @DisplayName("[F-13] 탈퇴·강퇴 서비스가 던진 오류는 코드와 상태 그대로 응답한다")
    void membershipErrorsAreMapped() {
        // given
        doThrow(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT))
                .doThrow(new BusinessException(BasecampErrorCode.BASECAMP_LEADER_CANNOT_LEAVE))
                .when(basecampMembershipService)
                .leave(77L, MEMBER_ID);
        doThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .doThrow(new BusinessException(BasecampErrorCode.BASECAMP_INVALID_STATE))
                .when(basecampMembershipService)
                .kick(77L, 31L, MEMBER_ID, KickReason.OTHER);

        // when
        MvcTestResult lowTrust =
                mvc.delete().uri(LEAVE).with(verified()).with(csrf()).exchange();
        MvcTestResult leader =
                mvc.delete().uri(LEAVE).with(verified()).with(csrf()).exchange();
        MvcTestResult denied = postKick(verified(), "{\"reason\":\"OTHER\"}");
        MvcTestResult invalidState = postKick(verified(), "{\"reason\":\"OTHER\"}");

        // then
        assertThat(lowTrust).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(lowTrust).bodyJson().extractingPath("$.code").isEqualTo("TRUST_LEVEL_INSUFFICIENT");
        assertThat(leader).hasStatus(HttpStatus.CONFLICT);
        assertThat(leader).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_LEADER_CANNOT_LEAVE");
        assertThat(denied).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(invalidState).hasStatus(HttpStatus.CONFLICT);
        assertThat(invalidState).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_INVALID_STATE");
    }

    private MvcTestResult postKick(RequestPostProcessor login, String requestBody) {
        return mvc.post()
                .uri(KICK)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();
    }

    private MvcTestResult postApply(RequestPostProcessor login, String requestBody) {
        return mvc.post()
                .uri(APPLICATIONS)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();
    }

    private MvcTestResult post(RequestPostProcessor login, String requestBody) {
        return mvc.post()
                .uri(BASECAMPS)
                .with(login)
                .with(csrf())
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
}
