package com.moewadeul.common.architecture;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class MapperTableAccessTest {

    private static final String COMMON = "common";

    private static final Path MAIN_MAPPER_ROOT = Path.of("src/main/resources/mapper");

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
            entry("program", Set.of("member", "spot")),
            entry("review", Set.of("member")),
            entry(
                    "spot",
                    Set.of(
                            "basecamp",
                            "basecamp_member",
                            "program",
                            "program_application",
                            "review_image",
                            "solo_trip",
                            "spot_review",
                            "sync_job_run",
                            "member")),
            entry("trip", Set.of("member", "spot")),
            entry("trust", Set.of("basecamp", "basecamp_application", "basecamp_member", "member")));

    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/fixtures/mapper-table-access");

    private static final Set<String> STATEMENTS = Set.of("select", "insert", "update", "delete");
    private static final Pattern NAMESPACE_MODULE = Pattern.compile("^com\\.moewadeul\\.(\\w+)\\.");
    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{(\\w+)}");
    private static final String TABLE = "\\s+`?([a-z_][a-z0-9_]*)`?";
    private static final Pattern READ = Pattern.compile("\\b(?:FROM|JOIN)" + TABLE, Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_FROM =
            Pattern.compile("\\bDELETE(?:\\s+[a-z_][a-z0-9_]*)?\\s+FROM" + TABLE, Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT =
            Pattern.compile("\\b(?:INSERT(?:\\s+IGNORE)?|REPLACE)\\s+INTO" + TABLE, Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE =
            Pattern.compile("\\b((?:FOR|KEY)\\s+)?UPDATE" + TABLE, Pattern.CASE_INSENSITIVE);
    private static final Pattern UNRESOLVED_TABLE =
            Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE)\\s+`?(\\$\\{\\w+})", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_OR_JOIN = Pattern.compile("\\b(?:FROM|JOIN)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_NAME = Pattern.compile("`?[a-z_$][a-z0-9_{}]*`?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIAS_THEN_COMMA =
            Pattern.compile("(?:\\s+(?:AS\\s+)?[a-z_][a-z0-9_]*)?\\s*,", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("[ADR-005] 매퍼 SQL은 자기 모듈이 소유했거나 허용된 교차 읽기 테이블만 건드린다")
    void mapperSqlTouchesOnlyAllowedTables() throws Exception {
        Scan scan = scan(MAIN_MAPPER_ROOT);

        assertThat(scan.statementCount()).as("검사한 매퍼 문장").isPositive();
        assertThat(scan.violations()).as("매퍼 테이블 접근 위반").isEmpty();
        assertThat(scan.crossReads()).as("교차 읽기 허용 표와 실제 교차 읽기").isEqualTo(CROSS_MODULE_READS);
    }

    @Test
    @DisplayName("include한 조각의 테이블은 include한 문장의 모듈로 세고, 조각만 있는 파일은 판정하지 않는다")
    void fragmentTablesCountForIncludingModule() throws Exception {
        assertThat(scan(FIXTURE_ROOT.resolve("fragment-module")).violations())
                .containsExactly("aisearch/AiSearchProbeMapper.xml#lockMember: aisearch -> member (소유: member)");
    }

    @Test
    @DisplayName("include의 property로 넘긴 테이블 이름을 치환해 판정하고, 값이 없으면 실패한다")
    void tablePropertiesAreSubstituted() throws Exception {
        assertThat(scan(FIXTURE_ROOT.resolve("table-property")).violations())
                .containsExactlyInAnyOrder(
                        "notification/NotificationProbeMapper.xml#readOther: notification -> sanction (소유: trust)",
                        "notification/NotificationProbeMapper.xml#deleteOther: notification -> sanction"
                                + " (쓰기는 소유 모듈만 가능, 소유: trust)",
                        "notification/NotificationProbeMapper.xml#missingTable: 테이블 자리의 ${table} 값을 찾지 못했다",
                        "notification/NotificationProbeMapper.xml#missingQuotedTable: 테이블 자리의 ${table} 값을 찾지 못했다");
    }

    @Test
    @DisplayName("INSERT IGNORE INTO와 REPLACE INTO도 쓰기로 판정한다")
    void insertVariantsAreWrites() throws Exception {
        assertThat(scan(FIXTURE_ROOT.resolve("insert-variants")).violations())
                .containsExactlyInAnyOrder(
                        "trip/TripProbeMapper.xml#insertIgnore: trip -> basecamp (쓰기는 소유 모듈만 가능, 소유: basecamp)",
                        "trip/TripProbeMapper.xml#replace: trip -> program (쓰기는 소유 모듈만 가능, 소유: program)");
    }

    @Test
    @DisplayName("FROM·JOIN 뒤 테이블이나 파생 테이블 다음의 쉼표 조인은 뒤 테이블을 놓치므로 실패한다")
    void commaJoinIsRejected() throws Exception {
        assertThat(scan(FIXTURE_ROOT.resolve("comma-join")).violations())
                .containsExactlyInAnyOrder(
                        "trip/TripProbeMapper.xml#commaJoin: 쉼표 조인 대신 JOIN을 쓴다",
                        "trip/TripProbeMapper.xml#joinComma: 쉼표 조인 대신 JOIN을 쓴다",
                        "trip/TripProbeMapper.xml#derivedComma: 쉼표 조인 대신 JOIN을 쓴다");
    }

    private record Scan(int statementCount, List<String> violations, Map<String, Set<String>> crossReads) {}

    private record MapperFile(String file, String namespace, Element root) {}

    private static Scan scan(Path root) throws Exception {
        List<MapperFile> mappers = load(root);
        Map<String, Element> fragments = new HashMap<>();
        for (MapperFile mapper : mappers) {
            for (Element sql : children(mapper.root(), "sql")) {
                fragments.put(mapper.namespace() + "." + sql.getAttribute("id"), sql);
            }
        }

        int statementCount = 0;
        Set<String> violations = new LinkedHashSet<>();
        Map<String, Set<String>> crossReads = new HashMap<>();
        for (MapperFile mapper : mappers) {
            Matcher moduleMatcher = NAMESPACE_MODULE.matcher(mapper.namespace());
            if (!moduleMatcher.find()) {
                violations.add(mapper.file() + ": namespace가 com.moewadeul.<module> 형식이 아니다");
                continue;
            }
            String module = moduleMatcher.group(1);
            for (Element statement : children(mapper.root(), STATEMENTS)) {
                statementCount++;
                StringBuilder sql = new StringBuilder();
                expand(statement, Map.of(), mapper.namespace(), fragments, sql);
                String where = mapper.file() + "#" + statement.getAttribute("id");
                check(sql.toString(), where, module, violations, crossReads);
            }
        }
        return new Scan(statementCount, List.copyOf(violations), crossReads);
    }

    private static void check(
            String sql, String where, String module, Set<String> violations, Map<String, Set<String>> crossReads) {
        Matcher unresolved = UNRESOLVED_TABLE.matcher(sql);
        while (unresolved.find()) {
            violations.add(where + ": 테이블 자리의 " + unresolved.group(1) + " 값을 찾지 못했다");
        }
        if (hasCommaJoin(sql)) {
            violations.add(where + ": 쉼표 조인 대신 JOIN을 쓴다");
        }

        Set<String> writes = new HashSet<>();
        collect(INSERT.matcher(sql), writes);
        collect(DELETE_FROM.matcher(sql), writes);
        Matcher update = UPDATE.matcher(sql);
        while (update.find()) {
            if (update.group(1) == null) {
                writes.add(update.group(2).toLowerCase());
            }
        }
        Set<String> reads = new HashSet<>();
        collect(READ.matcher(sql), reads);
        reads.removeAll(writes);

        for (String table : writes) {
            String owner = TABLE_OWNERS.get(table);
            if (owner == null) {
                violations.add(where + ": 알 수 없는 테이블 -> " + table);
            } else if (!owner.equals(module) && !owner.equals(COMMON)) {
                violations.add(where + ": " + module + " -> " + table + " (쓰기는 소유 모듈만 가능, 소유: " + owner + ")");
            }
        }
        for (String table : reads) {
            String owner = TABLE_OWNERS.get(table);
            if (owner == null) {
                violations.add(where + ": 알 수 없는 테이블 -> " + table);
            } else if (!owner.equals(module) && !owner.equals(COMMON)) {
                if (CROSS_MODULE_READS.getOrDefault(module, Set.of()).contains(table)) {
                    crossReads.computeIfAbsent(module, key -> new HashSet<>()).add(table);
                } else {
                    violations.add(where + ": " + module + " -> " + table + " (소유: " + owner + ")");
                }
            }
        }
    }

    // MyBatis처럼 include를 펼쳐 조각의 테이블이 include한 문장의 모듈로 잡히게 한다
    private static void expand(
            Node node,
            Map<String, String> properties,
            String namespace,
            Map<String, Element> fragments,
            StringBuilder out) {
        NodeList nodes = node.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node child = nodes.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                out.append(substitute(child.getNodeValue(), properties));
            } else if (child instanceof Element element && element.getTagName().equals("include")) {
                String refid = substitute(element.getAttribute("refid"), properties);
                String qualified = refid.contains(".") ? refid : namespace + "." + refid;
                Element fragment = fragments.get(qualified);
                if (fragment == null) {
                    throw new IllegalStateException("include할 조각을 찾지 못했다: " + qualified);
                }
                Map<String, String> inner = new HashMap<>(properties);
                for (Element property : children(element, "property")) {
                    inner.put(property.getAttribute("name"), substitute(property.getAttribute("value"), properties));
                }
                expand(fragment, inner, namespace, fragments, out);
            } else if (child instanceof Element) {
                expand(child, properties, namespace, fragments, out);
            }
            out.append(' ');
        }
    }

    private static boolean hasCommaJoin(String sql) {
        Matcher source = FROM_OR_JOIN.matcher(sql);
        while (source.find()) {
            int end;
            if (sql.charAt(source.end()) == '(') {
                end = afterClosingParen(sql, source.end());
            } else {
                Matcher name = TABLE_NAME.matcher(sql).region(source.end(), sql.length());
                if (!name.lookingAt()) {
                    continue;
                }
                end = name.end();
            }
            if (ALIAS_THEN_COMMA.matcher(sql).region(end, sql.length()).lookingAt()) {
                return true;
            }
        }
        return false;
    }

    private static int afterClosingParen(String sql, int open) {
        int depth = 0;
        for (int i = open; i < sql.length(); i++) {
            if (sql.charAt(i) == '(') {
                depth++;
            } else if (sql.charAt(i) == ')' && --depth == 0) {
                return i + 1;
            }
        }
        return sql.length();
    }

    private static String substitute(String text, Map<String, String> properties) {
        return PROPERTY_REF
                .matcher(text)
                .replaceAll(ref -> Matcher.quoteReplacement(properties.getOrDefault(ref.group(1), ref.group())));
    }

    private static List<MapperFile> load(Path root) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.filter(path -> path.toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        }
        List<MapperFile> mappers = new ArrayList<>();
        for (Path path : paths) {
            Element mapper = builder.parse(path.toFile()).getDocumentElement();
            mappers.add(new MapperFile(root.relativize(path).toString(), mapper.getAttribute("namespace"), mapper));
        }
        return mappers;
    }

    private static List<Element> children(Element parent, String tag) {
        return children(parent, Set.of(tag));
    }

    private static List<Element> children(Element parent, Set<String> tags) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && tags.contains(element.getTagName())) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static void collect(Matcher matcher, Set<String> into) {
        while (matcher.find()) {
            into.add(matcher.group(1).toLowerCase());
        }
    }
}
