package com.moewadeul.admin.api.spot;

import static com.moewadeul.common.testsupport.PageEnvelope.assertEnvelope;
import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.moderation.ModerationResult;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.spot.application.moderation.AdminSpotQueryService;
import com.moewadeul.spot.application.moderation.AdminSpotSummary;
import com.moewadeul.spot.application.moderation.SpotModerationService;
import com.moewadeul.spot.domain.SpotErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(AdminSpotController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class AdminSpotControllerTest {

    private static final String SPOTS = "/api/admin/spots";
    private static final Instant AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final long ADMIN_ID = 1L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AdminSpotQueryService spotQueryService;

    @MockitoBean
    private SpotModerationService spotModerationService;

    @Test
    @DisplayName("[F-21] 로그인하지 않았거나 관리자가 아닌 회원이 박지 검토 API를 부르면 401, 403이다")
    void spotApisRequireAdmin() {
        MvcTestResult anonymous = mvc.get().uri(SPOTS).exchange();
        MvcTestResult anonymousHide =
                mvc.post().uri(SPOTS + "/9/hide").with(csrf()).exchange();
        MvcTestResult userList = mvc.get().uri(SPOTS).with(member("USER")).exchange();
        MvcTestResult userHide = post(member("USER"), SPOTS + "/9/hide", null);
        MvcTestResult userRestore = post(member("USER"), SPOTS + "/9/restore", null);

        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousHide).hasStatus(HttpStatus.UNAUTHORIZED);
        for (MvcTestResult result : List.of(userList, userHide, userRestore)) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        }
        verifyNoInteractions(spotQueryService, spotModerationService);
    }

    @Test
    @DisplayName("[F-21] 박지 검토 목록은 제보자, 사유별 신고 수, 최근 신고를 담아 응답하고 status를 생략하면 서비스에 null을 넘긴다")
    void spotListReturnsSummaries() {
        var summary = new AdminSpotSummary(
                9L,
                "BAKJI",
                "능선 끝 평지",
                "PENDING_REVIEW",
                37.25,
                127.25,
                true,
                new AdminSpotSummary.Reporter(4L, "제보자"),
                5L,
                Map.of("ILLEGAL_AREA", 2L, "CLOSED", 1L, "FALSE_INFO", 2L),
                List.of(new AdminSpotSummary.RecentReport("CLOSED", "길이 막혔다", AT)),
                AT);
        var publicSpot = new AdminSpotSummary(
                10L,
                "CAMPSITE",
                "야영장",
                "PENDING_REVIEW",
                37.3,
                127.3,
                false,
                null,
                0L,
                Map.of("ILLEGAL_AREA", 0L, "CLOSED", 0L, "FALSE_INFO", 0L),
                List.of(),
                AT);
        when(spotQueryService.list(eq(null), anyInt(), anyInt()))
                .thenReturn(new PageSlice<>(List.of(summary, publicSpot), 0, 20, false));

        MvcTestResult result = mvc.get().uri(SPOTS).with(admin()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].spotId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.content[0].type").isEqualTo("BAKJI");
        assertThat(result).bodyJson().extractingPath("$.content[0].parkWarning").isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reporter.nickname")
                .isEqualTo("제보자");
        assertThat(result).bodyJson().extractingPath("$.content[0].reportCount").isEqualTo(5);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.ILLEGAL_AREA")
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.CLOSED")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.FALSE_INFO")
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].recentReports[0].content")
                .isEqualTo("길이 막혔다");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].statusChangedAt")
                .isEqualTo("2026-10-05T03:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[1].reporter").isNull();
        assertEnvelope(result, 0, 20, false);
    }

    @Test
    @DisplayName("[F-21] 박지 검토 목록의 size가 1~50을 벗어나거나 page가 음수이면 400 INVALID_INPUT이고, 알 수 없는 status는 서비스가 400으로 거부한다")
    void spotListRejectsInvalidParameters() {
        for (String[] param : new String[][] {{"size", "51"}, {"size", "0"}, {"page", "-1"}}) {
            MvcTestResult result =
                    mvc.get().uri(SPOTS).param(param[0], param[1]).with(admin()).exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(spotQueryService);
        when(spotQueryService.list(eq("ACTIVE"), anyInt(), anyInt()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "status가 올바르지 않습니다."));

        MvcTestResult unknown =
                mvc.get().uri(SPOTS).param("status", "ACTIVE").with(admin()).exchange();

        assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-21] 장소 숨김과 복구는 로그인한 관리자 ID로 서비스를 부르고 장소 ID와 상태를 응답한다")
    void hideAndRestoreReturnStatus() {
        when(spotModerationService.hide(9L, ADMIN_ID)).thenReturn(new ModerationResult(9L, "HIDDEN"));
        when(spotModerationService.restore(9L, ADMIN_ID)).thenReturn(new ModerationResult(9L, "ACTIVE"));

        MvcTestResult hide = post(admin(), SPOTS + "/9/hide", null);
        MvcTestResult restore = post(admin(), SPOTS + "/9/restore", null);

        assertThat(hide).hasStatus(HttpStatus.OK);
        assertThat(hide).bodyJson().extractingPath("$.spotId").isEqualTo(9);
        assertThat(hide).bodyJson().extractingPath("$.status").isEqualTo("HIDDEN");
        assertThat(restore).hasStatus(HttpStatus.OK);
        assertThat(restore).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-21] 서비스가 던진 NOT_FOUND는 404로, SPOT_INVALID_STATE는 409로 응답한다")
    void hideAndRestoreMapErrors() {
        when(spotModerationService.hide(eq(1L), anyLong())).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(spotModerationService.restore(eq(2L), anyLong()))
                .thenThrow(new BusinessException(SpotErrorCode.SPOT_INVALID_STATE));

        MvcTestResult notFound = post(admin(), SPOTS + "/1/hide", null);
        MvcTestResult conflict = post(admin(), SPOTS + "/2/restore", null);

        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
        assertThat(conflict).bodyJson().extractingPath("$.code").isEqualTo("SPOT_INVALID_STATE");
    }

    private MvcTestResult post(RequestPostProcessor login, String path, String body) {
        var request = mvc.post().uri(path).with(login).with(csrf());
        if (body == null) {
            return request.exchange();
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
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
