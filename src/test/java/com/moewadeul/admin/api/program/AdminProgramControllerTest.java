package com.moewadeul.admin.api.program;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.program.application.catalog.ProgramAdminService;
import com.moewadeul.program.application.catalog.ProgramApplicantItem;
import com.moewadeul.program.application.catalog.ProgramApplicantPage;
import com.moewadeul.program.application.catalog.ProgramApplicantQuery;
import com.moewadeul.program.application.catalog.ProgramCancelResult;
import com.moewadeul.program.application.catalog.ProgramCreateCommand;
import com.moewadeul.program.application.catalog.ProgramDetail;
import com.moewadeul.program.application.catalog.ProgramReviseCommand;
import com.moewadeul.program.domain.ProgramErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(AdminProgramController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class AdminProgramControllerTest {

    private static final String PROGRAMS = "/api/admin/programs";
    private static final Instant AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final long ADMIN_ID = 1L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProgramAdminService programAdminService;

    @Test
    @DisplayName("[F-17] 로그인하지 않았거나 관리자가 아닌 회원이 행사 관리 API를 부르면 401, 403이다")
    void programApisRequireAdmin() {
        MvcTestResult anonymousCreate = mvc.post().uri(PROGRAMS).with(csrf()).exchange();
        MvcTestResult anonymousList =
                mvc.get().uri(PROGRAMS + "/9/applications").exchange();
        MvcTestResult userCreate = post(member("USER"), PROGRAMS, validCreateBody());
        MvcTestResult userPatch = patch(member("USER"), PROGRAMS + "/9", "{\"fee\":0}");
        MvcTestResult userCancel = post(member("USER"), PROGRAMS + "/9/cancel", null);
        MvcTestResult userList =
                mvc.get().uri(PROGRAMS + "/9/applications").with(member("USER")).exchange();

        assertThat(anonymousCreate).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousList).hasStatus(HttpStatus.UNAUTHORIZED);
        for (MvcTestResult result : List.of(userCreate, userPatch, userCancel, userList)) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        }
        verifyNoInteractions(programAdminService);
    }

    @Test
    @DisplayName("[F-17] 관리자라도 행사 변경 요청에 CSRF 토큰이 없으면 403이다")
    void programWithoutCsrfIsForbidden() {
        MvcTestResult result = mvc.post()
                .uri(PROGRAMS)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateBody())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(programAdminService);
    }

    @Test
    @DisplayName("[F-17] 행사 등록은 로그인한 관리자 ID로 서비스를 부르고 201과 programId를 응답한다")
    void createProgramReturnsCreated() {
        when(programAdminService.create(eq(ADMIN_ID), any(ProgramCreateCommand.class)))
                .thenReturn(31L);

        MvcTestResult result = post(admin(), PROGRAMS, validCreateBody());

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.programId").isEqualTo(31);
        ArgumentCaptor<ProgramCreateCommand> captor = ArgumentCaptor.forClass(ProgramCreateCommand.class);
        verify(programAdminService).create(eq(ADMIN_ID), captor.capture());
        assertThat(captor.getValue().capacity()).isEqualTo(200);
        assertThat(captor.getValue().paymentDeadlineMinutes()).isNull();
        assertThat(captor.getValue().applyOpenAt()).isEqualTo(Instant.parse("2026-11-01T01:00:00Z"));
    }

    @Test
    @DisplayName("[F-17] 행사 등록의 필수 값이 없거나 범위를 벗어나면 400 INVALID_INPUT과 fieldErrors이다")
    void createProgramRejectsInvalidBody() {
        String body = validCreateBody()
                .replace("\"capacity\":200", "\"capacity\":1001")
                .replace("\"fee\":30000", "\"fee\":-1")
                .replace("\"title\":\"가을 백패킹\",", "")
                .replace("\"paymentDeadlineMinutes\":null", "\"paymentDeadlineMinutes\":1441");

        MvcTestResult result = post(admin(), PROGRAMS, body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        for (String field : List.of("title", "capacity", "fee", "paymentDeadlineMinutes")) {
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.fieldErrors[?(@.field=='" + field + "')]")
                    .asList()
                    .hasSize(1);
        }
        verifyNoInteractions(programAdminService);
    }

    @Test
    @DisplayName("[F-17] 서비스가 던진 INVALID_INPUT은 400으로 응답한다")
    void createProgramMapsInvalidInput() {
        when(programAdminService.create(anyLong(), any(ProgramCreateCommand.class)))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "시각 순서가 틀립니다."));

        MvcTestResult result = post(admin(), PROGRAMS, validCreateBody());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-17] 행사 수정은 보낸 필드만 서비스에 넘기고, spotId를 null로 보내면 요청에 있는 null로 넘긴다")
    void reviseProgramPassesPresentFieldsOnly() {
        when(programAdminService.revise(eq(ADMIN_ID), eq(9L), any(ProgramReviseCommand.class)))
                .thenReturn(programDetail());

        MvcTestResult result = patch(admin(), PROGRAMS + "/9", "{\"capacity\":250,\"spotId\":null}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.programId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
        assertThat(result).bodyJson().extractingPath("$.paymentDeadlineMinutes").isEqualTo(15);
        assertThat(result).bodyJson().doesNotHavePath("$.myApplication");
        ArgumentCaptor<ProgramReviseCommand> captor = ArgumentCaptor.forClass(ProgramReviseCommand.class);
        verify(programAdminService).revise(eq(ADMIN_ID), eq(9L), captor.capture());
        ProgramReviseCommand command = captor.getValue();
        assertThat(command.capacity()).isEqualTo(250);
        assertThat(command.title()).isNull();
        assertThat(command.spotId().present()).isTrue();
        assertThat(command.spotId().value()).isNull();
    }

    @Test
    @DisplayName("[F-17] 행사 수정에 보내지 않은 spotId는 요청에 없는 것으로 넘긴다")
    void reviseProgramTreatsMissingSpotIdAsAbsent() {
        when(programAdminService.revise(anyLong(), anyLong(), any(ProgramReviseCommand.class)))
                .thenReturn(programDetail());

        MvcTestResult result = patch(admin(), PROGRAMS + "/9", "{\"fee\":0}");

        assertThat(result).hasStatus(HttpStatus.OK);
        ArgumentCaptor<ProgramReviseCommand> captor = ArgumentCaptor.forClass(ProgramReviseCommand.class);
        verify(programAdminService).revise(eq(ADMIN_ID), eq(9L), captor.capture());
        assertThat(captor.getValue().spotId().present()).isFalse();
        assertThat(captor.getValue().fee()).isZero();
    }

    @Test
    @DisplayName("[F-17] 행사 수정에 고칠 필드가 없거나 값이 범위를 벗어나면 400 INVALID_INPUT이다")
    void reviseProgramRejectsInvalidBody() {
        MvcTestResult empty = patch(admin(), PROGRAMS + "/9", "{}");
        MvcTestResult outOfRange = patch(admin(), PROGRAMS + "/9", "{\"capacity\":0,\"title\":\" \"}");

        for (MvcTestResult result : List.of(empty, outOfRange)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(outOfRange)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='capacity')]")
                .asList()
                .hasSize(1);
        verifyNoInteractions(programAdminService);
    }

    @Test
    @DisplayName("[F-17] 서비스가 던진 PROGRAM_CAPACITY_DECREASE, PROGRAM_INVALID_STATE는 409, NOT_FOUND는 404로 응답한다")
    void reviseAndCancelProgramMapErrors() {
        when(programAdminService.revise(eq(ADMIN_ID), eq(1L), any(ProgramReviseCommand.class)))
                .thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_CAPACITY_DECREASE));
        when(programAdminService.revise(eq(ADMIN_ID), eq(2L), any(ProgramReviseCommand.class)))
                .thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE));
        when(programAdminService.cancel(ADMIN_ID, 3L))
                .thenThrow(new BusinessException(ProgramErrorCode.PROGRAM_INVALID_STATE));
        when(programAdminService.cancel(ADMIN_ID, 4L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        MvcTestResult decrease = patch(admin(), PROGRAMS + "/1", "{\"capacity\":1}");
        MvcTestResult canceledRevise = patch(admin(), PROGRAMS + "/2", "{\"capacity\":1}");
        MvcTestResult canceledAgain = post(admin(), PROGRAMS + "/3/cancel", null);
        MvcTestResult missing = post(admin(), PROGRAMS + "/4/cancel", null);

        assertThat(decrease).hasStatus(HttpStatus.CONFLICT);
        assertThat(decrease).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_CAPACITY_DECREASE");
        assertThat(canceledRevise).hasStatus(HttpStatus.CONFLICT);
        assertThat(canceledRevise).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_INVALID_STATE");
        assertThat(canceledAgain).hasStatus(HttpStatus.CONFLICT);
        assertThat(canceledAgain).bodyJson().extractingPath("$.code").isEqualTo("PROGRAM_INVALID_STATE");
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-17] 행사 취소는 행사 ID, 상태, 함께 취소된 신청 수를 응답한다")
    void cancelProgramReturnsResult() {
        when(programAdminService.cancel(ADMIN_ID, 9L)).thenReturn(new ProgramCancelResult(9L, "CANCELED", 4));

        MvcTestResult result = post(admin(), PROGRAMS + "/9/cancel", null);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.programId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CANCELED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.canceledApplicationCount")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("[F-17] 신청자 목록은 회원 ID와 닉네임만 담아 응답하고 status를 서비스에 넘긴다")
    void applicationsReturnsApplicants() {
        var item = new ProgramApplicantItem(
                7L,
                new ProgramApplicantItem.Applicant(5L, "신청자"),
                "CANCELED",
                AT,
                null,
                AT,
                "PROGRAM_CANCELED",
                AT,
                AT);
        when(programAdminService.applications(eq(9L), any(ProgramApplicantQuery.class)))
                .thenReturn(new ProgramApplicantPage(List.of(item), 0, 20, true));

        MvcTestResult result = mvc.get()
                .uri(PROGRAMS + "/9/applications")
                .param("status", "CANCELED")
                .with(admin())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].applicationId")
                .isEqualTo(7);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].member.memberId")
                .isEqualTo(5);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].member.nickname")
                .isEqualTo("신청자");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].member.email");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].cancelReason")
                .isEqualTo("PROGRAM_CANCELED");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        verify(programAdminService).applications(9L, new ProgramApplicantQuery("CANCELED", 0, 20));
    }

    @Test
    @DisplayName("[F-17] 신청자 목록의 status가 허용 값이 아니거나 size가 1~50을 벗어나면 400 INVALID_INPUT이다")
    void applicationsRejectInvalidParameters() {
        for (String[] param : new String[][] {{"status", "PENDING"}, {"size", "51"}, {"size", "0"}, {"page", "-1"}}) {
            MvcTestResult result = mvc.get()
                    .uri(PROGRAMS + "/9/applications")
                    .param(param[0], param[1])
                    .with(admin())
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(programAdminService);
    }

    private MvcTestResult post(RequestPostProcessor login, String path, String body) {
        var request = mvc.post().uri(path).with(login).with(csrf());
        if (body == null) {
            return request.exchange();
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult patch(RequestPostProcessor login, String path, String body) {
        return mvc.patch()
                .uri(path)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String validCreateBody() {
        return """
                {"title":"가을 백패킹","description":"설명","spotId":null,"locationText":"설악산 입구",\
                "startAt":"2026-11-14T01:00:00Z","endAt":"2026-11-15T05:00:00Z","capacity":200,"fee":30000,\
                "applyOpenAt":"2026-11-01T01:00:00Z","applyCloseAt":"2026-11-10T01:00:00Z",\
                "paymentDeadlineMinutes":null,"overnight":true}""";
    }

    private static ProgramDetail programDetail() {
        return new ProgramDetail(
                9L,
                "가을 백패킹",
                "설명",
                "설악산 입구",
                null,
                Instant.parse("2026-11-14T01:00:00Z"),
                Instant.parse("2026-11-15T05:00:00Z"),
                Instant.parse("2026-11-01T01:00:00Z"),
                Instant.parse("2026-11-10T01:00:00Z"),
                250,
                250,
                30000,
                15,
                true,
                "OPEN",
                null);
    }

    private static RequestPostProcessor admin() {
        return member("ADMIN");
    }

    private static RequestPostProcessor member(String role) {
        LoginMember loginMember = new LoginMember(ADMIN_ID, role, true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }
}
