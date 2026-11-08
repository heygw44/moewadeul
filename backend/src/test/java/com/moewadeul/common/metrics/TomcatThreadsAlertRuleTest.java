package com.moewadeul.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TomcatThreadsAlertRuleTest {

    private static final Path ALERT_RULES = Path.of("../deploy/grafana/alert-rules.json");
    private static final Pattern TOMCAT_SELECTOR = Pattern.compile("tomcat_threads_[a-z_]+\\{[^}]*}");

    @Test
    @DisplayName("[NFR-09] Tomcat 스레드 경보는 요청 커넥터의 스레드만 세고 관리 포트 커넥터의 스레드는 세지 않는다")
    void tomcatThreadSelectorsNameOnlyTheRequestConnector() throws IOException {
        // given
        Properties prod = prodProperties();
        String requestConnector = "http-nio-" + prod.getProperty("server.port", "8080");
        String managementConnector = "http-nio-" + prod.getProperty("management.server.port");

        // when
        List<String> selectors = tomcatSelectors();

        // then
        assertThat(managementConnector).isNotEqualTo(requestConnector);
        assertThat(selectors)
                .isNotEmpty()
                .allSatisfy(selector -> assertThat(selector).contains("name=\"" + requestConnector + "\""));
    }

    private static Properties prodProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"), new ClassPathResource("application-prod.yml"));
        return yaml.getObject();
    }

    private static List<String> tomcatSelectors() throws IOException {
        JsonNode rules = JsonMapper.builder()
                .build()
                .readTree(Files.readString(ALERT_RULES))
                .path("rules");
        List<String> selectors = new ArrayList<>();
        for (JsonNode rule : rules) {
            for (JsonNode query : rule.path("data")) {
                Matcher matcher =
                        TOMCAT_SELECTOR.matcher(query.path("model").path("expr").asString(""));
                while (matcher.find()) {
                    selectors.add(matcher.group());
                }
            }
        }
        return selectors;
    }
}
