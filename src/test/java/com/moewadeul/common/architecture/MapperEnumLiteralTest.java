package com.moewadeul.common.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

class MapperEnumLiteralTest {

    private static final String ROOT_PACKAGE = "com.moewadeul";
    private static final Set<String> STATEMENT_TAGS = Set.of("select", "insert", "update", "delete");

    private static final int CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE;
    private static final Pattern STRING_LITERAL = Pattern.compile("'([^']*)'");
    private static final Pattern COMPARISON = Pattern.compile("((?:\\w+\\.)?\\w+)\\s*(?:=|<>|!=)\\s*'([^']*)'");
    private static final Pattern IN_LIST = Pattern.compile(
            "((?:\\w+\\.)?\\w+)\\s+(?:NOT\\s+)?IN\\s*\\(\\s*('[^']*'(?:\\s*,\\s*'[^']*')*)\\s*\\)", CASE_INSENSITIVE);
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?<!\\bFOR\\s)(?<!\\bKEY\\s)\\b(?:FROM|JOIN|UPDATE|INTO)\\s+`?([a-z_][a-z0-9_]*)`?"
                    + "(?:\\s+(?:AS\\s+)?([a-z_][a-z0-9_]*))?",
            CASE_INSENSITIVE);
    private static final Set<String> NOT_AN_ALIAS = Set.of(
            "where",
            "set",
            "on",
            "using",
            "left",
            "right",
            "inner",
            "outer",
            "cross",
            "join",
            "group",
            "order",
            "limit",
            "having",
            "union",
            "for",
            "select",
            "values",
            "as",
            "and",
            "or",
            "lock",
            "partition");
    private static final Pattern UPPER_CASE_CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    private static Map<String, Map<String, Class<? extends Enum<?>>>> enumColumnsByTable;
    private static Set<String> entityTables;

    private record Scope(Map<String, Set<String>> tablesByAlias, Set<String> tables) {}

    private record Resolution(Class<? extends Enum<?>> enumType, String reason) {

        static Resolution of(Class<? extends Enum<?>> enumType) {
            return new Resolution(enumType, null);
        }

        static Resolution unresolved(String reason) {
            return new Resolution(null, reason);
        }
    }

    private record Finding(String file, String statementId, String column, String literal, String detail) {

        @Override
        public String toString() {
            return "%s [%s] 열 %s 에 리터럴 '%s' (%s)".formatted(file, statementId, column, literal, detail);
        }
    }

    private static final class Report {
        final List<Finding> mismatches = new ArrayList<>();
        final Map<String, List<Finding>> unclassifiedByReason = new TreeMap<>();
        int checked;
        int statements;
    }

    @BeforeAll
    static void collectEnumColumns() {
        enumColumnsByTable = new HashMap<>();
        new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(ROOT_PACKAGE).stream()
                        .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                        .map(JavaClass::reflect)
                        .forEach(entity -> {
                            Map<String, Class<? extends Enum<?>>> columns = enumColumnsByTable.computeIfAbsent(
                                    entity.getAnnotation(Table.class).name(), k -> new HashMap<>());
                            collectEnumFields(entity, columns);
                        });
        entityTables = enumColumnsByTable.keySet();
    }

    @SuppressWarnings("unchecked")
    private static void collectEnumFields(Class<?> type, Map<String, Class<? extends Enum<?>>> columns) {
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Enumerated enumerated = field.getAnnotation(Enumerated.class);
                if (enumerated != null
                        && enumerated.value() == EnumType.STRING
                        && field.getType().isEnum()) {
                    columns.put(columnName(field), (Class<? extends Enum<?>>) field.getType());
                } else if (field.isAnnotationPresent(Embedded.class)) {
                    collectEnumFields(field.getType(), columns);
                }
            }
        }
    }

    // 프로젝트는 물리 네이밍 전략을 따로 두지 않아, Spring Boot 기본(camelCase → snake_case)이 적용된다.
    private static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isEmpty()) {
            return column.name();
        }
        return field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    @Test
    @DisplayName("매퍼 SQL이 enum 열과 비교하는 문자열 리터럴은 소유 enum의 상수다")
    void mapperLiteralsAreConstantsOfTheOwningEnum() throws Exception {
        Report report = scanMappers();

        printSummary(report);

        assertThat(report.mismatches)
                .as("매퍼 XML 리터럴이 enum 상수와 다르다. enum 상수 이름을 바꿨다면 매퍼 SQL도 함께 고쳐야 한다.")
                .isEmpty();
    }

    private static Report scanMappers() throws IOException, ParserConfigurationException, SAXException {
        Report report = new Report();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setCoalescing(true);

        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml");
        assertThat(resources).as("mapper XML을 찾지 못했다").isNotEmpty();

        Map<String, Element> fragments = new LinkedHashMap<>();
        Map<String, String> fragmentFiles = new HashMap<>();
        Map<String, List<Element>> statementsByFile = new LinkedHashMap<>();
        for (Resource resource : resources) {
            String file = resource.getURL().getPath().replaceFirst(".*/mapper/", "");
            Element root;
            try (var in = resource.getInputStream()) {
                root = factory.newDocumentBuilder().parse(in).getDocumentElement();
            }
            String namespace = root.getAttribute("namespace");
            for (Element child : childElements(root)) {
                if (child.getTagName().equals("sql")) {
                    String key = namespace + "." + child.getAttribute("id");
                    fragments.put(key, child);
                    fragmentFiles.put(key, file);
                    child.setUserData("namespace", namespace, null);
                } else if (STATEMENT_TAGS.contains(child.getTagName())) {
                    child.setUserData("namespace", namespace, null);
                    statementsByFile
                            .computeIfAbsent(file, k -> new ArrayList<>())
                            .add(child);
                }
            }
        }

        Set<String> includedFragments = new HashSet<>();
        statementsByFile.forEach((file, statements) -> {
            for (Element statement : statements) {
                String sql =
                        sqlText(statement, (String) statement.getUserData("namespace"), fragments, includedFragments);
                scanStatement(report, file, statement.getAttribute("id"), sql);
            }
        });
        fragments.forEach((key, fragment) -> {
            if (!includedFragments.contains(key)) {
                String sql = sqlText(fragment, (String) fragment.getUserData("namespace"), fragments, new HashSet<>());
                scanStatement(report, fragmentFiles.get(key), "sql#" + fragment.getAttribute("id"), sql);
            }
        });
        return report;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    // <include>는 SQL 조각을 그 자리에 펼친다. 별칭은 조각이 아니라 이를 부른 문장이 정하기 때문이다.
    private static String sqlText(
            Node node, String namespace, Map<String, Element> fragments, Set<String> includedFragments) {
        StringBuilder sql = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && element.getTagName().equals("include")) {
                String refid = element.getAttribute("refid");
                String key = refid.contains(".") ? refid : namespace + "." + refid;
                includedFragments.add(key);
                Element fragment = fragments.get(key);
                if (fragment != null) {
                    String fragmentNamespace = (String) fragment.getUserData("namespace");
                    sql.append(' ')
                            .append(sqlText(fragment, fragmentNamespace, fragments, includedFragments))
                            .append(' ');
                }
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sql.append(child.getTextContent());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                sql.append(' ')
                        .append(sqlText(child, namespace, fragments, includedFragments))
                        .append(' ');
            }
        }
        return sql.toString().replaceAll("\\s+", " ");
    }

    private static void scanStatement(Report report, String file, String statementId, String sql) {
        report.statements++;
        Scope scope = scopeOf(sql);
        Set<Integer> classifiedLiterals = new HashSet<>();

        Matcher comparison = COMPARISON.matcher(sql);
        while (comparison.find()) {
            classifiedLiterals.add(comparison.start(2) - 1);
            check(report, file, statementId, scope, comparison.group(1), comparison.group(2));
        }
        Matcher inList = IN_LIST.matcher(sql);
        while (inList.find()) {
            Matcher literals = STRING_LITERAL.matcher(inList.group(2));
            while (literals.find()) {
                classifiedLiterals.add(inList.start(2) + literals.start());
                check(report, file, statementId, scope, inList.group(1), literals.group(1));
            }
        }

        Matcher literal = STRING_LITERAL.matcher(sql);
        while (literal.find()) {
            if (!classifiedLiterals.contains(literal.start())
                    && UPPER_CASE_CODE.matcher(literal.group(1)).matches()) {
                String context = sql.substring(Math.max(0, literal.start() - 30), literal.start())
                        .trim();
                report.unclassifiedByReason
                        .computeIfAbsent("비교 형태가 아님(THEN/ELSE/VALUES 등)", k -> new ArrayList<>())
                        .add(new Finding(file, statementId, "..." + context, literal.group(1), ""));
            }
        }
    }

    private static Scope scopeOf(String sql) {
        Map<String, Set<String>> tablesByAlias = new HashMap<>();
        Set<String> tables = new LinkedHashSet<>();
        Matcher ref = TABLE_REF.matcher(sql);
        while (ref.find()) {
            String table = ref.group(1).toLowerCase();
            tables.add(table);
            tablesByAlias.computeIfAbsent(table, k -> new HashSet<>()).add(table);
            String alias = ref.group(2);
            if (alias != null && !NOT_AN_ALIAS.contains(alias.toLowerCase())) {
                tablesByAlias
                        .computeIfAbsent(alias.toLowerCase(), k -> new HashSet<>())
                        .add(table);
            }
        }
        return new Scope(tablesByAlias, tables);
    }

    private static void check(
            Report report, String file, String statementId, Scope scope, String columnRef, String literal) {
        int dot = columnRef.indexOf('.');
        String qualifier = dot < 0 ? null : columnRef.substring(0, dot).toLowerCase();
        String column = columnRef.substring(dot + 1).toLowerCase();

        Resolution resolution = resolve(scope, qualifier, column);
        if (resolution.enumType() == null) {
            report.unclassifiedByReason
                    .computeIfAbsent(resolution.reason(), k -> new ArrayList<>())
                    .add(new Finding(file, statementId, columnRef, literal, ""));
            return;
        }
        report.checked++;
        boolean known = Arrays.stream(resolution.enumType().getEnumConstants())
                .anyMatch(c -> c.name().equals(literal));
        if (!known) {
            String allowed = Arrays.toString(resolution.enumType().getEnumConstants());
            report.mismatches.add(new Finding(
                    file,
                    statementId,
                    columnRef,
                    literal,
                    "%s의 상수가 아님. 허용 값: %s".formatted(resolution.enumType().getSimpleName(), allowed)));
        }
    }

    private static Resolution resolve(Scope scope, String qualifier, String column) {
        Set<String> candidates =
                qualifier == null ? scope.tables() : scope.tablesByAlias().get(qualifier);
        if (candidates == null || candidates.isEmpty()) {
            return Resolution.unresolved("별칭을 표로 풀지 못함(서브쿼리 별칭 등)");
        }
        Set<Class<? extends Enum<?>>> enums = new HashSet<>();
        for (String table : candidates) {
            if (!entityTables.contains(table)) {
                return Resolution.unresolved("엔티티가 없는 표(Flyway로만 만든 표)");
            }
            Class<? extends Enum<?>> enumType = enumColumnsByTable.get(table).get(column);
            if (enumType != null) {
                enums.add(enumType);
            }
        }
        if (enums.isEmpty()) {
            return Resolution.unresolved("엔티티의 enum 열이 아님(@Enumerated(STRING) 없음)");
        }
        if (enums.size() > 1) {
            return Resolution.unresolved("같은 이름의 열이 서로 다른 enum으로 겹침");
        }
        return Resolution.of(enums.iterator().next());
    }

    private static void printSummary(Report report) {
        int unclassified = report.unclassifiedByReason.values().stream()
                .mapToInt(List::size)
                .sum();
        StringBuilder out = new StringBuilder("%n[매퍼 enum 리터럴 검사] 문장 %d개, enum 대조 %d건, 분류 못 함 %d건, 불일치 %d건%n"
                .formatted(report.statements, report.checked, unclassified, report.mismatches.size()));
        report.unclassifiedByReason.forEach((reason, findings) -> {
            out.append("  분류 못 함 - %s: %d건%n".formatted(reason, findings.size()));
            findings.forEach(f ->
                    out.append("    %s [%s] %s '%s'%n".formatted(f.file(), f.statementId(), f.column(), f.literal())));
        });
        report.mismatches.forEach(f -> out.append("  불일치: %s%n".formatted(f)));
        System.out.print(out);
    }
}
