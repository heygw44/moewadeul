package com.moewadeul.common.architecture;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapperTableAccessTest {

    private static final String COMMON = "common";

    // 테스트 전용 픽스처 매퍼(src/test/resources)가 classpath에 섞이지 않도록 main 소스만 읽는다
    private static final Path MAPPER_ROOT = Path.of("src/main/resources/mapper");

    private static final Map<String, String> TABLE_OWNERS = Map.ofEntries(
            entry("member", "member"),
            entry("email_verification", "member"),
            entry("email_verification_ip_lock", "member"),
            entry("login_attempt_lock", "member"),
            entry("login_history", "member"),
            entry("password_reset_throttle", "member"),
            entry("password_reset_token", "member"),
            entry("signup_duplicate_attempt", "member"),
            entry("disposable_email_domain", "member"),
            entry("companion_review", "trust"),
            entry("companion_review_tag", "trust"),
            entry("identity_verification", "trust"),
            entry("member_report", "trust"),
            entry("sanction", "trust"),
            entry("spot", "spot"),
            entry("bakji_confirmation", "spot"),
            entry("bakji_detail", "spot"),
            entry("bakji_report", "spot"),
            entry("protected_area", "spot"),
            entry("public_spot_detail", "spot"),
            entry("public_spot_setting", "spot"),
            entry("spot_cluster_cell", "spot"),
            entry("spot_cluster_total", "spot"),
            entry("spot_review", "review"),
            entry("review_image", "review"),
            entry("basecamp", "basecamp"),
            entry("basecamp_application", "basecamp"),
            entry("basecamp_member", "basecamp"),
            entry("program", "program"),
            entry("program_application", "program"),
            entry("payment", "program"),
            entry("program_vacancy_alert", "program"),
            entry("community_post", "community"),
            entry("community_comment", "community"),
            entry("community_image", "community"),
            entry("community_post_like", "community"),
            entry("community_report", "community"),
            entry("memory", "memory"),
            entry("memory_image", "memory"),
            entry("memory_like", "memory"),
            entry("memory_report", "memory"),
            entry("notification", "notification"),
            entry("notification_setting", "notification"),
            entry("solo_trip", "trip"),
            entry("sync_job_run", "publicdata"),
            entry("ai_search_usage", "aisearch"),
            entry("idempotency_record", "idempotency"),
            entry("outbox_event", COMMON),
            entry("admin_audit_log", COMMON));

    private static final Map<String, Set<String>> CROSS_MODULE_READS = Map.ofEntries(
            entry("basecamp", Set.of("member", "spot")),
            entry("community", Set.of("member", "spot")),
            entry("memory", Set.of("member", "spot")),
            entry("notification", Set.of("member")),
            // spot은 Java 의존 방향 없이 읽는 자리라 #820에서 공개 서비스로 바꾸고 지운다
            entry("program", Set.of("member", "spot")),
            entry("review", Set.of("member")),
            entry("spot", Set.of("basecamp", "basecamp_member", "program", "program_application",
                    "review_image", "solo_trip", "spot_review", "sync_job_run", "member")),
            entry("trip", Set.of("member", "spot")),
            entry("trust", Set.of("basecamp", "basecamp_application", "basecamp_member", "member")));

    private static final Pattern NAMESPACE = Pattern.compile("<mapper\\s+namespace=\"com\\.moewadeul\\.(\\w+)");
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final String TABLE = "\\s+`?([a-z_][a-z0-9_]*)`?";
    private static final Pattern READ = Pattern.compile("\\b(?:FROM|JOIN)" + TABLE, Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_FROM = Pattern.compile(
            "\\bDELETE(?:\\s+[a-z_][a-z0-9_]*)?\\s+FROM" + TABLE, Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT = Pattern.compile("\\bINSERT\\s+INTO" + TABLE, Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE = Pattern.compile(
            "(?<![</\\w])((?:FOR|KEY)\\s+)?UPDATE" + TABLE, Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_PROPERTY = Pattern.compile(
            "<property\\s+name=\"\\w*Table\"\\s+value=\"([a-z_][a-z0-9_]*)\"");

    @Test
    @DisplayName("[ADR-005] 매퍼 SQL은 자기 모듈이 소유했거나 허용된 교차 읽기 테이블만 건드린다")
    void mapperSqlTouchesOnlyAllowedTables() throws IOException {
        List<String> violations = new ArrayList<>();
        Set<String> usedCrossReads = new HashSet<>();

        List<Path> mapperFiles;
        try (Stream<Path> walk = Files.walk(MAPPER_ROOT)) {
            mapperFiles = walk.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        }
        assertThat(mapperFiles).as("매퍼 XML").isNotEmpty();

        for (Path path : mapperFiles) {
            String file = MAPPER_ROOT.relativize(path).toString();
            String xml = XML_COMMENT.matcher(Files.readString(path, StandardCharsets.UTF_8)).replaceAll("");
            Matcher namespace = NAMESPACE.matcher(xml);
            if (!namespace.find()) {
                violations.add(file + ": namespace가 com.moewadeul.<module> 형식이 아니다");
                continue;
            }
            String module = namespace.group(1);

            Set<String> writes = new HashSet<>();
            collect(INSERT.matcher(xml), writes);
            collect(DELETE_FROM.matcher(xml), writes);
            Matcher update = UPDATE.matcher(xml);
            while (update.find()) {
                if (update.group(1) == null) {
                    writes.add(update.group(2).toLowerCase());
                }
            }

            Set<String> reads = new HashSet<>();
            collect(READ.matcher(xml), reads);
            collect(TABLE_PROPERTY.matcher(xml), reads);

            for (String table : writes) {
                String owner = TABLE_OWNERS.get(table);
                if (owner == null) {
                    violations.add(file + ": 알 수 없는 테이블 -> " + table);
                } else if (!owner.equals(module) && !owner.equals(COMMON)) {
                    violations.add(file + ": " + module + " -> " + table + " (쓰기는 소유 모듈만 가능, 소유: " + owner + ")");
                }
            }
            for (String table : reads) {
                String owner = TABLE_OWNERS.get(table);
                if (owner == null) {
                    violations.add(file + ": 알 수 없는 테이블 -> " + table);
                } else if (!owner.equals(module) && !owner.equals(COMMON)) {
                    if (CROSS_MODULE_READS.getOrDefault(module, Set.of()).contains(table)) {
                        usedCrossReads.add(module + " -> " + table);
                    } else {
                        violations.add(file + ": " + module + " -> " + table + " (소유: " + owner + ")");
                    }
                }
            }
        }

        CROSS_MODULE_READS.forEach((module, tables) -> tables.forEach(table -> {
            if (!usedCrossReads.contains(module + " -> " + table)) {
                violations.add("CROSS_MODULE_READS: " + module + " -> " + table + " 을 읽는 매퍼가 없다, 항목을 지운다");
            }
        }));

        assertThat(violations).as("매퍼 테이블 접근 위반").isEmpty();
    }

    private static void collect(Matcher matcher, Set<String> into) {
        while (matcher.find()) {
            into.add(matcher.group(1).toLowerCase());
        }
    }
}
