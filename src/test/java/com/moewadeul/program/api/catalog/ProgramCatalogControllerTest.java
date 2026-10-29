package com.moewadeul.program.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.program.application.catalog.MyProgramApplicationItem;
import com.moewadeul.program.application.catalog.MyProgramApplicationPage;
import com.moewadeul.program.application.catalog.MyProgramApplicationQuery;
import com.moewadeul.program.application.catalog.ProgramDetail;
import com.moewadeul.program.application.catalog.ProgramListQuery;
import com.moewadeul.program.application.catalog.ProgramPage;
import com.moewadeul.program.application.catalog.ProgramQueryService;
import com.moewadeul.program.application.catalog.ProgramSummary;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramPhase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProgramCatalogController.class)
@Import({
    GlobalExceptionHandler.class,
    TraceIdFilter.class,
    SecurityConfig.class,
})
class ProgramCatalogControllerTest {

    private static final Instant AT = Instant.parse("2026-11-01T01:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProgramQueryService programQueryService;

    @Test
    @DisplayName("[F-17] 로그인하지 않아도 행사 목록을 조회하고, status와 page를 서비스에 넘긴다")
    void anonymousCanListPrograms() {
        ProgramSummary summary =
                new ProgramSummary(9L, "가을 백패킹", "설악산 입구", null, AT, AT, AT, AT, 200, 150, 30000, true, "OPEN");
        when(programQueryService.list(any(ProgramListQuery.class)))
                .thenReturn(new ProgramPage(List.of(summary), 1, 5, true, AT));

        MvcTestResult result = mvc.get()
                .uri("/api/programs")
                .param("status", "OPEN")
                .param("page", "1")
                .param("size", "5")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].programId").isEqualTo(9);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].remainingSeats")
                .isEqualTo(150);
        assertThat(result).bodyJson().extractingPath("$.content[0].spotId").isNull();
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("OPEN");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.nextApplyOpenAt").isEqualTo(AT.toString());
        verify(programQueryService).list(new ProgramListQuery(ProgramPhase.OPEN, 1, 5));
    }

    @Test
    @DisplayName("[F-17] 신청이 열릴 행사가 없으면 목록 응답에 nextApplyOpenAt 키가 없다")
    void listOmitsNextApplyOpenAtWhenNoUpcomingProgram() {
        when(programQueryService.list(any(ProgramListQuery.class)))
                .thenReturn(new ProgramPage(List.of(), 0, 20, false, null));

        MvcTestResult result = mvc.get().uri("/api/programs").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().doesNotHavePath("$.nextApplyOpenAt");
    }

    @Test
    @DisplayName("[F-17] 목록의 status가 UPCOMING, OPEN, CLOSED가 아니거나 size가 1~50을 벗어나면 400 INVALID_INPUT이다")
    void listRejectsInvalidParameters() {
        for (String[] param :
                new String[][] {{"status", "CANCELED"}, {"status", "open"}, {"size", "51"}, {"page", "-1"}}) {
            MvcTestResult result =
                    mvc.get().uri("/api/programs").param(param[0], param[1]).exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
    }

    @Test
    @DisplayName("[F-17] 목록의 status가 허용 값이 아니면 statusValid 필드에 허용 값 안내를 담아 400으로 응답한다")
    void listReportsAllowedStatusesInFieldError() {
        MvcTestResult result =
                mvc.get().uri("/api/programs").param("status", "CANCELED").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("statusValid");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].reason")
                .isEqualTo("status는 UPCOMING, OPEN, CLOSED 중 하나여야 해요.");
    }

    @Test
    @DisplayName("[F-18] 내 행사 신청 목록의 status가 허용 값이 아니면 statusValid 필드에 허용 값 안내를 담아 400으로 응답한다")
    void listMineReportsAllowedStatusesInFieldError() {
        MvcTestResult result = mvc.get()
                .uri("/api/me/program-applications")
                .param("status", "OPEN")
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("statusValid");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].reason")
                .isEqualTo("status는 PENDING_PAYMENT, CONFIRMED, CANCELED, EXPIRED 중 하나여야 해요.");
    }

    @Test
    @DisplayName("[F-17] 비로그인 요청자의 상세 응답에는 myApplication 필드가 없다")
    void anonymousDetailOmitsMyApplication() {
        when(programQueryService.detail(9L, null)).thenReturn(detail(null));

        MvcTestResult result = mvc.get().uri("/api/programs/9").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.programId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("설명");
        assertThat(result).bodyJson().doesNotHavePath("$.myApplication");
    }

    @Test
    @DisplayName("[F-17] 신청이 있는 회원의 상세 응답에는 myApplication이 있고, 로그인한 회원 ID로 서비스를 부른다")
    void memberDetailIncludesMyApplication() {
        when(programQueryService.detail(eq(9L), eq(4L)))
                .thenReturn(detail(new ProgramDetail.MyApplication(70L, "PENDING_PAYMENT", AT)));

        MvcTestResult result = mvc.get().uri("/api/programs/9").with(member(4L)).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.myApplication.applicationId")
                .isEqualTo(70);
        assertThat(result).bodyJson().extractingPath("$.myApplication.status").isEqualTo("PENDING_PAYMENT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.myApplication.paymentDueAt")
                .isEqualTo("2026-11-01T01:00:00Z");
    }

    @Test
    @DisplayName("[F-17] 없는 행사의 상세는 404 NOT_FOUND이다")
    void missingProgramIsNotFound() {
        when(programQueryService.detail(404L, null)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        MvcTestResult result = mvc.get().uri("/api/programs/404").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-18] 내 행사 신청 목록은 로그인한 회원 ID와 조회 조건으로 서비스를 부르고, 시각과 사유가 없는 필드는 null로 응답한다")
    void listMineReturnsPage() {
        MyProgramApplicationItem item = new MyProgramApplicationItem(
                31L,
                "PENDING_PAYMENT",
                AT,
                null,
                null,
                null,
                AT,
                new MyProgramApplicationItem.Program(4L, "가을 능선 백패킹", "지리산 성삼재", AT, AT, 30000, "OPEN"));
        when(programQueryService.listMine(4L, new MyProgramApplicationQuery(ProgramApplicationStatus.CONFIRMED, 1, 5)))
                .thenReturn(new MyProgramApplicationPage(List.of(item), 1, 5, true));

        MvcTestResult result = mvc.get()
                .uri("/api/me/program-applications")
                .param("status", "CONFIRMED")
                .param("page", "1")
                .param("size", "5")
                .with(member(4L))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].applicationId")
                .isEqualTo(31);
        assertThat(result).bodyJson().extractingPath("$.content[0].confirmedAt").isNull();
        assertThat(result).bodyJson().extractingPath("$.content[0].program.fee").isEqualTo(30000);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].program.status")
                .isEqualTo("OPEN");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.content[0]").asMap().containsKey("cancelReason");
    }

    @Test
    @DisplayName("[F-18] 내 행사 신청 목록의 status가 허용 값이 아니거나 size가 1~50을 벗어나면 400 INVALID_INPUT이다")
    void listMineRejectsInvalidParameters() {
        for (String[] param :
                new String[][] {{"status", "OPEN"}, {"status", "confirmed"}, {"size", "51"}, {"page", "-1"}}) {
            MvcTestResult result = mvc.get()
                    .uri("/api/me/program-applications")
                    .param(param[0], param[1])
                    .with(member(4L))
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verify(programQueryService, never()).listMine(anyLong(), any());
    }

    @Test
    @DisplayName("[F-18] 로그인하지 않고 내 행사 신청 목록을 조회하면 401 AUTHENTICATION_REQUIRED이다")
    void listMineRequiresLogin() {
        MvcTestResult result = mvc.get().uri("/api/me/program-applications").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private static ProgramDetail detail(ProgramDetail.MyApplication mine) {
        return new ProgramDetail(
                9L, "가을 백패킹", "설명", "설악산 입구", null, AT, AT, AT, AT, 200, 150, 30000, 15, true, "OPEN", mine);
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
}
