package com.moewadeul.trust.api.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import com.moewadeul.member.application.profile.MemberProfile;
import com.moewadeul.member.application.profile.MemberProfileService;
import com.moewadeul.trust.application.profile.MemberProfileQueryService;
import com.moewadeul.trust.application.profile.MemberTrustProfile;
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

@WebMvcTest(MemberProfileController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class MemberProfileControllerTest {

    private static final long TARGET_ID = 9L;
    private static final String PATH = "/api/members/9/profile";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MemberProfileService memberProfileService;

    @MockitoBean
    private MemberProfileQueryService memberProfileQueryService;

    @Test
    @DisplayName("[NFR-11] 비로그인 요청은 memberId와 nickname만 응답하고 신뢰 정보를 조회하지 않는다")
    void anonymousSeesOnlyIdAndNickname() {
        when(memberProfileService.find(TARGET_ID))
                .thenReturn(new MemberProfile(TARGET_ID, "산바람", AgeGroup.THIRTIES, Gender.FEMALE));

        MvcTestResult result = mvc.get().uri(PATH).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                { "memberId": 9, "nickname": "산바람" }
                """);
        verifyNoInteractions(memberProfileQueryService);
    }

    @Test
    @DisplayName("[NFR-11] 로그인한 회원은 연령대·성별·신뢰 정보를 보고, 값이 없으면 null로 응답한다")
    void loggedInMemberSeesTrustFields() {
        when(memberProfileQueryService.find(TARGET_ID))
                .thenReturn(new MemberTrustProfile(
                        TARGET_ID,
                        "산바람",
                        null,
                        false,
                        Gender.FEMALE,
                        true,
                        TrustLevel.IDENTITY_VERIFIED,
                        0,
                        null,
                        List.of()));

        MvcTestResult result = mvc.get().uri(PATH).with(login(false)).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "memberId": 9,
                  "nickname": "산바람",
                  "ageGroup": null,
                  "ageGroupVerified": false,
                  "gender": "FEMALE",
                  "genderVerified": true,
                  "trustLevel": 1,
                  "completedCompanions": 0,
                  "companionReviewSummary": { "rejoinRate": null, "topTags": [] }
                }
                """);
        verifyNoInteractions(memberProfileService);
    }

    @Test
    @DisplayName("없는 회원이면 404 NOT_FOUND다")
    void missingMemberIsNotFound() {
        when(memberProfileService.find(TARGET_ID)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(memberProfileQueryService.find(TARGET_ID)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        MvcTestResult anonymous = mvc.get().uri(PATH).exchange();
        MvcTestResult loggedIn = mvc.get().uri(PATH).with(login(true)).exchange();

        assertThat(anonymous).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(anonymous).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(loggedIn).hasStatus(HttpStatus.NOT_FOUND);
    }

    private static RequestPostProcessor login(boolean emailVerified) {
        LoginMember loginMember = new LoginMember(7L, "USER", emailVerified);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
