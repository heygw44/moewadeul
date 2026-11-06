package com.moewadeul.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AlertRuleNoDataCoverageTest {

    private static final Path ALERT_RULES = Path.of("../deploy/grafana/alert-rules.json");
    private static final String MISSING_SERIES_RULE = "moewadeul-metrics-missing";
    private static final Pattern SELECTOR = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*\\{[^}]*}");

    private static final Map<String, String> LEGITIMATELY_ABSENT = Map.of(
            "http_server_requests_seconds_count", "앱을 다시 띄운 뒤 첫 요청 전에는 없다",
            "http_server_requests_seconds_bucket", "앱을 다시 띄운 뒤 첫 지도 요청 전에는 없다",
            "moewadeul_external_api_requests_seconds_count", "외부 API 첫 호출 전에는 없다",
            "probe_ssl_earliest_cert_expiry", "탐침이 실패하면 없고, 그 실패는 탐침 경보가 잡는다");
    private static final String FIRST_502_SELECTOR =
            "caddy_http_request_duration_seconds_count{job=\"caddy\",code=\"502\"}";

    @Test
    @DisplayName("[NFR-09] noDataState가 OK인 규칙의 시계열은 끊김 경보가 지켜보거나, 없어도 되는 이유가 있다")
    void everyQuietRuleSelectorIsWatchedOrExplained() throws IOException {
        JsonNode rules = rules();

        assertThat(unwatchedSelectors(rules)).isEmpty();
    }

    @Test
    @DisplayName("[NFR-09] 라벨이 어긋난 규칙이 끊김 경보 밖에 있으면 찾아낸다")
    void detectsSelectorDriftingFromTheWatchList() {
        JsonNode rules = JsonMapper.builder().build().readTree("""
                {"rules": [
                  {"uid": "moewadeul-metrics-missing", "noDataState": "OK",
                   "data": [{"model": {"expr": "absent(node_memory_MemTotal_bytes{job=\\"integrations/unix\\"})"}}]},
                  {"uid": "memory", "noDataState": "OK",
                   "data": [{"model": {"expr": "node_memory_MemTotal_bytes{job=\\"node\\"}"}}]},
                  {"uid": "backup", "noDataState": "Alerting",
                   "data": [{"model": {"expr": "backup_last_success{job=\\"node\\"}"}}]}
                ]}
                """).path("rules");

        assertThat(unwatchedSelectors(rules)).containsExactly("memory: node_memory_MemTotal_bytes{job=\"node\"}");
    }

    private static Set<String> unwatchedSelectors(JsonNode rules) {
        Set<String> watched = new TreeSet<>();
        for (JsonNode rule : rules) {
            if (MISSING_SERIES_RULE.equals(rule.path("uid").asString())) {
                watched.addAll(selectorsOf(rule));
            }
        }
        Set<String> unwatched = new TreeSet<>();
        for (JsonNode rule : rules) {
            if (!"OK".equals(rule.path("noDataState").asString())
                    || MISSING_SERIES_RULE.equals(rule.path("uid").asString())) {
                continue;
            }
            for (String selector : selectorsOf(rule)) {
                String name = selector.substring(0, selector.indexOf('{'));
                if (!watched.contains(selector)
                        && !LEGITIMATELY_ABSENT.containsKey(name)
                        && !FIRST_502_SELECTOR.equals(selector)) {
                    unwatched.add(rule.path("uid").asString() + ": " + selector);
                }
            }
        }
        return unwatched;
    }

    private static Set<String> selectorsOf(JsonNode rule) {
        Set<String> selectors = new TreeSet<>();
        for (JsonNode query : rule.path("data")) {
            JsonNode expr = query.path("model").path("expr");
            if (expr.isString()) {
                Matcher matcher = SELECTOR.matcher(expr.asString());
                while (matcher.find()) {
                    selectors.add(matcher.group());
                }
            }
        }
        return selectors;
    }

    private static JsonNode rules() throws IOException {
        return JsonMapper.builder()
                .build()
                .readTree(Files.readString(ALERT_RULES))
                .path("rules");
    }
}
