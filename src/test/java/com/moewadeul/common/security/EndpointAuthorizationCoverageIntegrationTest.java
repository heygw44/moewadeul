package com.moewadeul.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.security.AuthorizationMatrix.Endpoint;
import com.moewadeul.common.testsupport.IntegrationTest;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 컨트롤러에 있는 API 엔드포인트가 모두 권한 표({@link AuthorizationMatrix})에 있는지 확인한다.
 *
 * <p>권한 테스트는 그 표의 행으로만 돈다. 그래서 개발자가 엔드포인트를 추가하고 표에 넣지 않으면, 그 엔드포인트는 권한 테스트 없이 머지된다.
 * 이 테스트는 그런 엔드포인트를 찾아 실패한다.
 */
@IntegrationTest
class EndpointAuthorizationCoverageIntegrationTest {

    private static final String API_PREFIX = "/api/";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]+}");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("[NFR-07][NFR-11] 컨트롤러의 모든 API 엔드포인트가 권한 표에 등급과 함께 들어 있다")
    void everyApiEndpointIsListedInAuthorizationMatrix() {
        List<Endpoint> listed = AuthorizationMatrix.all().toList();

        List<String> unlisted = apiEndpoints().stream()
                .filter(endpoint -> listed.stream().noneMatch(row -> matches(endpoint, row)))
                .map(endpoint -> endpoint.method() + " " + endpoint.path())
                .toList();

        assertThat(apiEndpoints()).isNotEmpty();
        assertThat(unlisted).isEmpty();
    }

    // 경로 템플릿(/api/spots/{spotId})과 메서드 쌍을 모은다. 메서드를 정하지 않은 매핑은 없어야 하므로 따로 다루지 않는다.
    private List<Endpoint> apiEndpoints() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> endpointsOf(info).stream())
                .filter(endpoint -> endpoint.path().startsWith(API_PREFIX))
                .toList();
    }

    private static List<Endpoint> endpointsOf(RequestMappingInfo info) {
        return info.getPatternValues().stream()
                .flatMap(path -> info.getMethodsCondition().getMethods().stream()
                        .map(method -> new Endpoint(HttpMethod.valueOf(method.name()), path)))
                .toList();
    }

    private static boolean matches(Endpoint template, Endpoint row) {
        if (template.method() != row.method()) {
            return false;
        }
        String regex = PATH_VARIABLE.matcher(Pattern.quote(template.path())).replaceAll("\\\\E[^/]+\\\\Q");
        return row.path().matches(regex);
    }
}
