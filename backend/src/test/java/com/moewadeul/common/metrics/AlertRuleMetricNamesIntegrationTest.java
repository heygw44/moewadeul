package com.moewadeul.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.testsupport.IntegrationTest;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// tomcat_threads_*는 실제 Tomcat이 떠야 나온다. 운영처럼 요청 포트와 관리 포트를 따로 정해 띄운다.
// 포트를 0으로 두면 커넥터 이름이 http-nio-auto-1이 되어 운영의 http-nio-8080과 모양이 달라진다.
@IntegrationTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = "server.tomcat.mbeanregistry.enabled=true")
class AlertRuleMetricNamesIntegrationTest {

    private static final Path ALERT_RULES = Path.of("../deploy/grafana/alert-rules.json");

    private static final Map<String, Path> EXTERNAL_PRODUCERS =
            Map.of("moewadeul_backup_last_success_timestamp_seconds", Path.of("../deploy/backup/backup.sh"));

    private static final Pattern APP_METRIC =
            Pattern.compile("\\b((?:moewadeul|http_server_requests|hikaricp_connections|tomcat_threads)_[a-z0-9_]+)");
    private static final Pattern CONNECTOR_NAME = Pattern.compile("[{,]name=\"([^\"]+)\"");
    private static final Pattern SAMPLE_LINE = Pattern.compile("^([a-zA-Z_:][a-zA-Z0-9_:]*)(\\{([^}]*)})? ");

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private ExternalApiMetrics externalApiMetrics;

    @DynamicPropertySource
    static void connectorPorts(DynamicPropertyRegistry registry) throws IOException {
        try (ServerSocket request = new ServerSocket(0);
                ServerSocket management = new ServerSocket(0)) {
            registry.add("server.port", request::getLocalPort);
            registry.add("management.server.port", management::getLocalPort);
        }
    }

    @Test
    @DisplayName("[NFR-09] 경보 규칙의 expr에 적힌 앱 지표 이름은 모두 /actuator/prometheus 응답에 실제로 나온다")
    void everyAppMetricNamedInAlertRulesIsScraped() throws Exception {
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
    void externallyProducedMetricsAreEmittedByTheirProducer() throws Exception {
        Set<String> expected = appMetricNamesInExprs();

        assertThat(expected).containsAll(EXTERNAL_PRODUCERS.keySet());
        for (Map.Entry<String, Path> producer : EXTERNAL_PRODUCERS.entrySet()) {
            assertThat(Files.readString(producer.getValue())).contains(producer.getKey());
        }
    }

    @Test
    @DisplayName("[NFR-09] 작업 실패 지표는 job 태그를 달고 나오고, 경보는 수집기가 바꿔 붙이는 exported_job으로 묶는다")
    void scheduledJobFailuresCarryJobLabel() throws Exception {
        List<String> failures = scrapeLines().stream()
                .filter(line -> line.startsWith("moewadeul_scheduled_job_failures_total{"))
                .toList();

        assertThat(failures).isNotEmpty();
        assertThat(failures).allSatisfy(line -> assertThat(line).containsPattern("[{,]job=\"[^\"]+\""));
        assertThat(exprs().stream()
                        .filter(expr -> expr.contains("increase(moewadeul_scheduled_job_failures_total"))
                        .toList())
                .isNotEmpty()
                .allSatisfy(expr -> assertThat(expr).contains("by (exported_job)"));
    }

    @Test
    @DisplayName("[NFR-09] 관리 포트를 따로 열어도 요청 포트의 Tomcat 스레드 지표가 경보가 거르는 http-nio-<요청 포트> name 라벨로 나온다")
    void requestConnectorThreadsCarryPortName() throws Exception {
        Set<String> connectors = new TreeSet<>();
        for (String line : scrapeLines()) {
            Matcher matcher = CONNECTOR_NAME.matcher(line);
            if (line.startsWith("tomcat_threads_busy_threads{") && matcher.find()) {
                connectors.add(matcher.group(1));
            }
        }

        assertThat(connectors).contains("http-nio-" + port);
    }

    private void createLazyMeters() throws IOException, InterruptedException {
        get(port, "/api/programs");
        externalApiMetrics.record("scrape-check", "probe", () -> "ok");
    }

    private List<String> scrapeLines() throws IOException, InterruptedException {
        createLazyMeters();
        return get(managementPort, "/actuator/prometheus").lines().toList();
    }

    private String get(int targetPort, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + targetPort + path))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
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
