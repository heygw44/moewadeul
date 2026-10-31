package com.moewadeul.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.testsupport.IntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@AutoConfigureMockMvc
class AlertRuleMetricNamesIntegrationTest {

    private static final Path ALERT_RULES = Path.of("deploy/grafana/alert-rules.json");

    // 앱이 아니라 서버의 수집 에이전트가 올리는 지표다. 생산자 파일에 이름이 실제로 남아 있는지도 함께 확인한다.
    private static final Map<String, Path> EXTERNAL_PRODUCERS =
            Map.of("moewadeul_backup_last_success_timestamp_seconds", Path.of("deploy/backup/backup.sh"));

    private static final Pattern APP_METRIC =
            Pattern.compile("\\b((?:moewadeul|http_server_requests|hikaricp_connections)_[a-z0-9_]+)");
    private static final Pattern SAMPLE_LINE = Pattern.compile("^([a-zA-Z_:][a-zA-Z0-9_:]*)(\\{([^}]*)})? ");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ExternalApiMetrics externalApiMetrics;

    @Test
    @DisplayName("[NFR-09] 경보 규칙의 expr에 적힌 앱 지표 이름은 모두 /actuator/prometheus 응답에 실제로 나온다")
    void everyAppMetricNamedInAlertRulesIsScraped() throws IOException {
        List<String> scrape = scrapeLines();

        Set<String> scrapedNames = new TreeSet<>();
        for (String line : scrape) {
            Matcher sample = SAMPLE_LINE.matcher(line);
            if (!line.startsWith("#") && sample.find()) {
                scrapedNames.add(sample.group(1));
            }
        }
        Set<String> expected = appMetricNamesInExprs();
        expected.removeAll(EXTERNAL_PRODUCERS.keySet());

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(scrapedNames);
        assertThat(expected).isNotEmpty();
        assertThat(missing).as("alert-rules.json 지표 중 스크랩에 없는 이름").isEmpty();
    }

    @Test
    @DisplayName("[NFR-09] 앱 밖에서 올라오는 것으로 허용한 지표는 생산자 스크립트가 실제로 내보낸다")
    void externallyProducedMetricsAreEmittedByTheirProducer() throws IOException {
        Set<String> expected = appMetricNamesInExprs();

        assertThat(expected).containsAll(EXTERNAL_PRODUCERS.keySet());
        for (Map.Entry<String, Path> producer : EXTERNAL_PRODUCERS.entrySet()) {
            assertThat(Files.readString(producer.getValue())).contains(producer.getKey());
        }
    }

    @Test
    @DisplayName("[NFR-09] 작업 실패 지표는 job 태그를 달고 나오고, 경보는 수집기가 바꿔 붙이는 exported_job으로 묶는다")
    void scheduledJobFailuresCarryJobLabel() throws IOException {
        List<String> failures = scrapeLines().stream()
                .filter(line -> line.startsWith("moewadeul_scheduled_job_failures_total{"))
                .toList();

        assertThat(failures).isNotEmpty();
        assertThat(failures).allSatisfy(line -> assertThat(line).containsPattern("[{,]job=\"[^\"]+\""));
        assertThat(exprs().stream()
                        .filter(expr -> expr.contains("moewadeul_scheduled_job_failures_total"))
                        .toList())
                .isNotEmpty()
                .allSatisfy(expr -> assertThat(expr).contains("by (exported_job)"));
    }

    // 외부 API 타이머와 HTTP 요청 지표는 처음 호출될 때 만들어지므로, 실제 코드 경로로 한 번씩 거치게 한다.
    private List<String> scrapeLines() throws IOException {
        mvc.get().uri("/actuator/health").exchange();
        externalApiMetrics.record("scrape-check", "probe", () -> "ok");
        String body =
                mvc.get().uri("/actuator/prometheus").exchange().getResponse().getContentAsString();
        return body.lines().toList();
    }

    private Set<String> appMetricNamesInExprs() throws IOException {
        Set<String> names = new TreeSet<>();
        for (String expr : exprs()) {
            Matcher matcher = APP_METRIC.matcher(expr);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private List<String> exprs() throws IOException {
        JsonNode rules = JsonMapper.builder()
                .build()
                .readTree(Files.readString(ALERT_RULES))
                .path("rules");
        List<String> exprs = new ArrayList<>();
        for (JsonNode rule : rules) {
            for (JsonNode query : rule.path("data")) {
                JsonNode expr = query.path("model").path("expr");
                if (expr.isString()) {
                    exprs.add(expr.asString());
                }
            }
        }
        return exprs;
    }
}
