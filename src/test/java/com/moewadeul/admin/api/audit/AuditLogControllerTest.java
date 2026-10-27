package com.moewadeul.admin.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.admin.application.audit.AuditLogPage;
import com.moewadeul.admin.application.audit.AuditLogQueryService;
import com.moewadeul.admin.application.audit.AuditLogView;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
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
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(AuditLogController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class AuditLogControllerTest {

    private static final String AUDIT_LOGS = "/api/admin/audit-logs";
    private static final Instant AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final long ADMIN_ID = 1L;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private AuditLogQueryService auditLogQueryService;

    @Test
    @DisplayName("[F-21] 감사 로그 조회는 필터를 서비스에 넘기고 detail을 JSON 객체로 응답한다")
    void auditLogsReturnDetailAsObject() {
        AuditLogView view = new AuditLogView(
                1L, ADMIN_ID, "REPORT_ACTION", "MEMBER_REPORT", 5L, jsonMapper.readTree("{\"hideReview\":true}"), AT);
        when(auditLogQueryService.findLogs(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AuditLogPage(List.of(view), 0, 20, true));

        MvcTestResult result = mvc.get()
                .uri(AUDIT_LOGS)
                .param("adminId", "1")
                .param("targetType", "MEMBER_REPORT")
                .param("from", "2026-10-05T00:00:00Z")
                .param("to", "2026-10-06T00:00:00Z")
                .with(admin())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].auditLogId").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].detail.hideReview")
                .isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        verify(auditLogQueryService)
                .findLogs(
                        1L,
                        "MEMBER_REPORT",
                        Instant.parse("2026-10-05T00:00:00Z"),
                        Instant.parse("2026-10-06T00:00:00Z"),
                        0,
                        20);
    }

    @Test
    @DisplayName("[F-21] 감사 로그 조회의 시각 형식이 틀리거나 size가 1~50을 벗어나면 400 INVALID_INPUT이다")
    void auditLogsRejectInvalidParameters() {
        for (String[] param : new String[][] {{"from", "어제"}, {"to", "2026-13-40"}, {"size", "51"}, {"size", "0"}}) {
            MvcTestResult result = mvc.get()
                    .uri(AUDIT_LOGS)
                    .param(param[0], param[1])
                    .with(admin())
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(auditLogQueryService);
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
