package com.moewadeul.aisearch.api;

import static com.moewadeul.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.moewadeul.aisearch.application.AiInterpretService;
import com.moewadeul.aisearch.application.AiSearchErrorCode;
import com.moewadeul.aisearch.application.AiSpotSetting;
import com.moewadeul.aisearch.application.AiSpotType;
import com.moewadeul.aisearch.application.SpotQueryConditions;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.GlobalExceptionHandler;
import com.moewadeul.common.error.RateLimitedException;
import com.moewadeul.common.security.LoginMember;
import com.moewadeul.common.security.SecurityConfig;
import com.moewadeul.common.trace.TraceIdFilter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

@WebMvcTest(AiInterpretController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class AiInterpretControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URI = "/api/spots/ai-interpret";
    // 응답 본문에 되돌아오는지 찾을 수 있도록 다른 곳에 나오지 않을 문장을 쓴다.
    private static final String SENTENCE = "쿠쿠다스봉우리 근처 계곡";
    private static final SpotQueryConditions EMPTY =
            new SpotQueryConditions(null, null, Set.of(), false, false, false, false, Set.of(), List.of());

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AiInterpretService aiInterpretService;

    @Test
    @DisplayName("[F-27][AI-01] 읽어 낸 조건만 JSON에 담고, 없는 조건은 키를 빼며, unparsed는 항상 담는다")
    void successOmitsAbsentConditions() {
        // given
        when(aiInterpretService.interpret(anyLong(), any()))
                .thenReturn(new SpotQueryConditions(
                        "강원도",
                        null,
                        Set.of(AiSpotType.BAKJI),
                        true,
                        false,
                        false,
                        false,
                        Set.of(AiSpotSetting.VALLEY),
                        List.of("조용한")));

        // when
        MvcTestResult result = send(verified(), "{\"query\":\"" + SENTENCE + "\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                        { "region": "강원도", "types": ["BAKJI"], "hasWater": true,
                          "settings": ["VALLEY"], "unparsed": ["조용한"] }
                        """);
        verify(aiInterpretService).interpret(MEMBER_ID, SENTENCE);
    }

    @Test
    @DisplayName("[F-27][AI-01] 아무 조건도 못 읽으면 unparsed 빈 배열만 응답한다")
    void emptyConditionsHaveOnlyUnparsed() {
        // given
        when(aiInterpretService.interpret(anyLong(), any())).thenReturn(EMPTY);

        // when
        MvcTestResult result = send(verified(), "{\"query\":\"" + SENTENCE + "\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"unparsed\": [] }");
    }

    @Test
    @DisplayName("[F-27][AI-01] 모든 조건을 읽으면 반경과 true인 플래그를 모두 담는다")
    void successWithAllConditions() {
        // given
        when(aiInterpretService.interpret(anyLong(), any()))
                .thenReturn(new SpotQueryConditions(
                        "설악산",
                        10.0,
                        Set.of(AiSpotType.CAMPSITE, AiSpotType.BAKJI),
                        true,
                        true,
                        true,
                        true,
                        Set.of(AiSpotSetting.VALLEY, AiSpotSetting.MOUNTAIN),
                        List.of()));

        // when
        MvcTestResult result = send(verified(), "{\"query\":\"" + SENTENCE + "\"}");

        // then
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                        { "region": "설악산", "radiusKm": 10.0, "types": ["CAMPSITE", "BAKJI"],
                          "hasWater": true, "hasToilet": true, "excludeWarning": true, "hideClosed": true,
                          "settings": ["MOUNTAIN", "VALLEY"], "unparsed": [] }
                        """);
    }

    @Test
    @DisplayName("[F-27][AI-01] 앞뒤 공백을 뺀 길이가 100자인 문장은 받는다")
    void acceptsHundredCharactersAroundSpaces() {
        // given
        when(aiInterpretService.interpret(anyLong(), any())).thenReturn(EMPTY);
        String hundred = "가".repeat(100);

        // when
        MvcTestResult result = send(verified(), "{\"query\":\"  " + hundred + "  \"}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(aiInterpretService).interpret(MEMBER_ID, "  " + hundred + "  ");
    }

    @Test
    @DisplayName("[F-27][AI-01] 이모지는 한 글자로 세어 100개까지 받는다")
    void countsEmojiAsOneCharacter() {
        // given
        when(aiInterpretService.interpret(anyLong(), any())).thenReturn(EMPTY);

        // when
        MvcTestResult result = send(verified(), "{\"query\":\"" + "🏕".repeat(100) + "\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {"{\"query\":\"가\"}", "{\"query\":\"   가   \"}", "{\"query\":\"     \"}", "{}", "{\"query\":null}"
            })
    @DisplayName("[F-27][AI-01] 공백을 뺀 길이가 2자 미만이거나 문장이 없으면 400 INVALID_INPUT과 query 필드 오류를 응답한다")
    void rejectsTooShortOrBlank(String body) {
        // when
        MvcTestResult result = send(verified(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='query')]")
                .asList()
                .hasSize(1);
        verifyNoInteractions(aiInterpretService);
    }

    @Test
    @DisplayName("[F-27][AI-01] 101자 문장은 400을 응답하고, 오류 응답에 입력한 문장을 되돌려 주지 않는다")
    void rejectsTooLongWithoutEchoingInput() {
        // given
        String tooLong = SENTENCE + "가".repeat(101);

        // when
        MvcTestResult result = send(verified(), "{\"query\":\"" + tooLong + "\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='query')]")
                .asList()
                .hasSize(1);
        assertThat(result).bodyText().doesNotContain(SENTENCE);
        verifyNoInteractions(aiInterpretService);
    }

    @Test
    @DisplayName("[F-27][AI-01] 로그인하지 않으면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousIsUnauthorized() {
        // when
        MvcTestResult result = mvc.post()
                .uri(URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + SENTENCE + "\"}")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(aiInterpretService);
    }

    @Test
    @DisplayName("[F-27][AI-01][TR-03] 이메일 인증 전의 회원은 403 MEMBER_NOT_VERIFIED를 응답한다")
    void unverifiedMemberIsForbidden() {
        // when
        MvcTestResult result = send(unverified(), "{\"query\":\"" + SENTENCE + "\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(aiInterpretService);
    }

    @Test
    @DisplayName("[F-27] CSRF 토큰이 없으면 403을 응답하고 서비스를 부르지 않는다")
    void missingCsrfIsForbidden() {
        // when
        MvcTestResult result = mvc.post()
                .uri(URI)
                .with(verified())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + SENTENCE + "\"}")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(aiInterpretService);
    }

    @Test
    @DisplayName("[F-27][AI-03] 서비스가 한도 초과를 던지면 429 AI_SEARCH_LIMITED와 Retry-After를 응답한다")
    void limitedReturnsTooManyRequests() {
        // given
        when(aiInterpretService.interpret(anyLong(), any()))
                .thenThrow(new RateLimitedException(AiSearchErrorCode.AI_SEARCH_LIMITED, Duration.ofSeconds(4321)));

        // when
        MvcTestResult result = send(verified(), "{\"query\":\"" + SENTENCE + "\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AI_SEARCH_LIMITED");
        assertThat(result).headers().hasValue("Retry-After", "4321");
    }

    @Test
    @DisplayName("[F-27][AI-05] 서비스가 이용 불가를 던지면 503 AI_SEARCH_UNAVAILABLE을 응답한다")
    void unavailableReturnsServiceUnavailable() {
        // given
        when(aiInterpretService.interpret(anyLong(), any()))
                .thenThrow(new BusinessException(AiSearchErrorCode.AI_SEARCH_UNAVAILABLE));

        // when
        MvcTestResult result = send(verified(), "{\"query\":\"" + SENTENCE + "\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AI_SEARCH_UNAVAILABLE");
        assertThat(result).bodyText().doesNotContain(SENTENCE);
    }

    private MvcTestResult send(RequestPostProcessor login, String body) {
        return mvc.post()
                .uri(URI)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
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
