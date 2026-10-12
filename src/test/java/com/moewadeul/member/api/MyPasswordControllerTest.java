package com.moewadeul.member.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.member.application.PasswordChangeCommand;
import com.moewadeul.member.application.PasswordChangeService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MyPasswordController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class MyPasswordControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String PATH = "/api/me/password";
    private static final String SESSION_ID = "current-session";
    private static final String VALID_BODY =
            "{\"currentPassword\":\"Current-pass1\",\"newPassword\":\"Changed-pass1\",\"newPasswordConfirm\":\"Changed-pass1\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PasswordChangeService passwordChangeService;

    @MockitoBean
    private LoginSessionManager loginSessionManager;

    @Test
    @DisplayName("[F-32][PW-07] 이메일 인증 전인 회원이 올바른 요청을 보내면 204이고, 현재 세션 ID를 넘겨 서비스를 부른 뒤 세션 ID를 재발급한다")
    void changesPassword() {
        MvcTestResult result = put(VALID_BODY, login());

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        ArgumentCaptor<PasswordChangeCommand> captor = ArgumentCaptor.forClass(PasswordChangeCommand.class);
        verify(passwordChangeService).change(captor.capture());
        PasswordChangeCommand command = captor.getValue();
        assertThat(command.memberId()).isEqualTo(MEMBER_ID);
        assertThat(command.currentPassword()).isEqualTo("Current-pass1");
        assertThat(command.newPassword()).isEqualTo("Changed-pass1");
        assertThat(command.currentSessionId()).isEqualTo(SESSION_ID);
        assertThat(command.toString()).doesNotContain("Current-pass1", "Changed-pass1", SESSION_ID);
        verify(loginSessionManager).changeSessionId(any());
    }

    @Test
    @DisplayName("[F-32][PW-07] 새 비밀번호 확인이 다르면 400 INVALID_INPUT이고 fieldErrors에 newPasswordConfirm이 있다")
    void confirmMismatchIsInvalidInput() {
        MvcTestResult result = put(
                "{\"currentPassword\":\"Current-pass1\",\"newPassword\":\"Changed-pass1\",\"newPasswordConfirm\":\"Other-pass1\"}",
                login());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("newPasswordConfirm");
        verifyNoInteractions(passwordChangeService, loginSessionManager);
    }

    @Test
    @DisplayName("[F-32][PW-07] 필드가 비어 있거나 없으면 400 INVALID_INPUT이고 서비스를 부르지 않는다")
    void blankFieldsAreInvalidInput() {
        List<String> bodies = List.of(
                "{\"currentPassword\":\"\",\"newPassword\":\"Changed-pass1\",\"newPasswordConfirm\":\"Changed-pass1\"}",
                "{\"currentPassword\":\"Current-pass1\",\"newPassword\":\" \",\"newPasswordConfirm\":\" \"}",
                "{\"currentPassword\":\"Current-pass1\",\"newPassword\":\"Changed-pass1\"}",
                "{}");
        for (String body : bodies) {
            MvcTestResult result = put(body, login());

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(passwordChangeService);
    }

    @Test
    @DisplayName("[F-32][PW-07] 로그인하지 않으면 401 AUTHENTICATION_REQUIRED이고 서비스를 부르지 않는다")
    void anonymousIsUnauthorized() {
        MvcTestResult result = mvc.put()
                .uri(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(passwordChangeService);
    }

    @Test
    @DisplayName("[F-32][PW-07] CSRF 토큰 없이 요청하면 403이고 서비스를 부르지 않는다")
    void missingCsrfTokenIsForbidden() {
        MvcTestResult result = mvc.put()
                .uri(PATH)
                .with(login())
                .session(new MockHttpSession(null, SESSION_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(passwordChangeService);
    }

    private MvcTestResult put(String body, RequestPostProcessor user) {
        return mvc.put()
                .uri(PATH)
                .with(csrf())
                .with(user)
                .session(new MockHttpSession(null, SESSION_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static RequestPostProcessor login() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
