package com.moewadeul.admin.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.moewadeul.memory.application.AdminMemoryPage;
import com.moewadeul.memory.application.AdminMemoryQueryService;
import com.moewadeul.memory.application.AdminMemorySummary;
import com.moewadeul.memory.application.MemoryModerationResult;
import com.moewadeul.memory.application.MemoryModerationService;
import com.moewadeul.memory.application.MemoryPhoto;
import com.moewadeul.memory.domain.MemoryErrorCode;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(AdminMemoryController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class AdminMemoryControllerTest {

    private static final String BASE = "/api/admin/memories";
    private static final Instant AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final long ADMIN_ID = 1L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AdminMemoryQueryService queryService;

    @MockitoBean
    private MemoryModerationService moderationService;

    @Test
    @DisplayName("[F-33][MM-11] 로그인하지 않으면 401, 관리자가 아닌 회원이 추억 기록 검토 API를 부르면 403 ACCESS_DENIED이다")
    void requireAdmin() {
        // when
        MvcTestResult anonymous = mvc.get().uri(BASE).exchange();
        MvcTestResult userList = mvc.get().uri(BASE).with(member("USER")).exchange();
        MvcTestResult userHide = post(member("USER"), BASE + "/9/hide");
        MvcTestResult userRestore = post(member("USER"), BASE + "/9/restore");

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        for (MvcTestResult result : List.of(userList, userHide, userRestore)) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        }
        verifyNoInteractions(queryService, moderationService);
    }

    @Test
    @DisplayName("[F-33][MM-11] 검토 목록은 대략 좌표, 사진, 사유별 신고 수, 최근 신고를 담아 응답하고 글이 없으면 content 필드를 뺀다")
    void listReturnsItems() {
        // given
        AdminMemorySummary summary = new AdminMemorySummary(
                3L,
                8L,
                "기록쓴이",
                37.57,
                127.12,
                LocalDate.of(2026, 9, 30),
                1,
                null,
                List.of(new MemoryPhoto(11L, "https://example.test/view")),
                "PENDING_REVIEW",
                5,
                new AdminMemorySummary.ReasonCounts(2, 0, 0, 0, 3, 0),
                List.of(new AdminMemorySummary.RecentReport("MONEY_SCAM", "송금 요구", AT)),
                AT);
        when(queryService.list(null, 0, 20)).thenReturn(new AdminMemoryPage(List.of(summary), 0, 20, false));

        // when
        MvcTestResult result = mvc.get().uri(BASE).with(admin()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].memoryId").isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo("기록쓴이");
        assertThat(result).bodyJson().extractingPath("$.content[0].approxLat").isEqualTo(37.57);
        assertThat(result).bodyJson().extractingPath("$.content[0].approxLng").isEqualTo(127.12);
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].content");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].images[0].imageId")
                .isEqualTo(11);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.MONEY_SCAM")
                .isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].recentReports[0].content")
                .isEqualTo("송금 요구");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-33][MM-11] page가 음수이거나 size가 1~50을 벗어나면 400 INVALID_INPUT이고 서비스를 부르지 않는다")
    void invalidPagingIsBadRequest() {
        for (String query : List.of("?page=-1", "?size=0", "?size=51")) {
            // when
            MvcTestResult result = mvc.get().uri(BASE + query).with(admin()).exchange();

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("[F-33][MM-11] 숨김과 복구는 로그인한 관리자 ID로 서비스를 부르고 처리 뒤 상태를 응답한다")
    void hideAndRestoreCallServiceWithAdminId() {
        // given
        when(moderationService.hide(9L, ADMIN_ID)).thenReturn(new MemoryModerationResult(9L, "HIDDEN"));
        when(moderationService.restore(9L, ADMIN_ID)).thenReturn(new MemoryModerationResult(9L, "ACTIVE"));

        // when
        MvcTestResult hide = post(admin(), BASE + "/9/hide");
        MvcTestResult restore = post(admin(), BASE + "/9/restore");

        // then
        assertThat(hide).hasStatus(HttpStatus.OK);
        assertThat(hide).bodyJson().isStrictlyEqualTo("{\"memoryId\":9,\"status\":\"HIDDEN\"}");
        assertThat(restore).bodyJson().isStrictlyEqualTo("{\"memoryId\":9,\"status\":\"ACTIVE\"}");
        verify(moderationService).hide(9L, ADMIN_ID);
        verify(moderationService).restore(9L, ADMIN_ID);
    }

    @Test
    @DisplayName("[F-33][MM-11] 서비스가 던진 NOT_FOUND는 404로, MEMORY_INVALID_STATE는 409로 응답한다")
    void mapsServiceErrors() {
        // given
        when(moderationService.hide(anyLong(), eq(ADMIN_ID)))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(moderationService.restore(anyLong(), eq(ADMIN_ID)))
                .thenThrow(new BusinessException(MemoryErrorCode.MEMORY_INVALID_STATE));
        when(queryService.list(eq("ACTIVE"), anyInt(), anyInt()))
                .thenThrow(new BusinessException(
                        CommonErrorCode.INVALID_INPUT, "status는 PENDING_REVIEW 또는 HIDDEN이어야 합니다."));

        // when
        MvcTestResult notFound = post(admin(), BASE + "/9/hide");
        MvcTestResult conflict = post(admin(), BASE + "/9/restore");
        MvcTestResult badStatus =
                mvc.get().uri(BASE + "?status=ACTIVE").with(admin()).exchange();

        // then
        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
        assertThat(conflict).bodyJson().extractingPath("$.code").isEqualTo("MEMORY_INVALID_STATE");
        assertThat(badStatus).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult post(RequestPostProcessor user, String uri) {
        return mvc.post().uri(uri).with(user).with(csrf()).exchange();
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
