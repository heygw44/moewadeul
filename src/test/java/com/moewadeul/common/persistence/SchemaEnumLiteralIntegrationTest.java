package com.moewadeul.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.domain.MemberStatus;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.publicdata.domain.SyncJobStatus;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.domain.SpotStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// MapperEnumLiteralTest는 매퍼 XML만 훑는다. DB가 직접 계산하는 생성 컬럼과 트리거의 상태 문자열은 여기서 최종 스키마로 대조한다.
@IntegrationTest
class SchemaEnumLiteralIntegrationTest {

    private static final Pattern STRING_LITERAL = Pattern.compile("'([^']*)'");
    private static final Pattern COMPARISON =
            Pattern.compile("((?:\\w+\\.)?\\w+)\\s*(?:=|<>|!=|<=>)\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_LIST = Pattern.compile(
            "((?:\\w+\\.)?\\w+)\\s+(?:NOT\\s+)?IN\\s*\\(\\s*('[^']*'(?:\\s*,\\s*'[^']*')*)\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_INTRODUCER = Pattern.compile("_[a-z0-9]+(?=\\\\?')");

    // 키는 "<생성 컬럼이 속한 표 또는 트리거가 걸린 표>:<비교 왼쪽 식>"이다.
    // 트리거의 v_status는 spot.status를 SELECT ... INTO로 담은 변수다.
    private static final Map<String, Class<? extends Enum<?>>> ENUM_BY_COMPARED_EXPRESSION = Map.of(
            "member:status", MemberStatus.class,
            "program_application:status", ProgramApplicationStatus.class,
            "public_spot_detail:operating_status", PublicSpotOperatingStatus.class,
            "sync_job_run:status", SyncJobStatus.class,
            "spot:new.status", SpotStatus.class,
            "spot:old.status", SpotStatus.class,
            "bakji_detail:v_status", SpotStatus.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[NFR-09] 생성 컬럼과 트리거가 상태 열과 비교하는 문자열은 모두 소유 enum의 상수다")
    void generatedColumnAndTriggerLiteralsAreEnumConstants() {
        List<String> problems = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();

        for (Map<String, Object> column : jdbcTemplate.queryForList("""
                SELECT TABLE_NAME, COLUMN_NAME, GENERATION_EXPRESSION FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND GENERATION_EXPRESSION IS NOT NULL AND GENERATION_EXPRESSION <> ''
                """)) {
            scan(
                    "생성 컬럼 %s.%s".formatted(column.get("TABLE_NAME"), column.get("COLUMN_NAME")),
                    (String) column.get("TABLE_NAME"),
                    (String) column.get("GENERATION_EXPRESSION"),
                    problems,
                    usedKeys);
        }
        for (Map<String, Object> trigger : jdbcTemplate.queryForList("""
                SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_STATEMENT FROM information_schema.TRIGGERS
                WHERE TRIGGER_SCHEMA = DATABASE()
                """)) {
            scan(
                    "트리거 %s".formatted(trigger.get("TRIGGER_NAME")),
                    (String) trigger.get("EVENT_OBJECT_TABLE"),
                    (String) trigger.get("ACTION_STATEMENT"),
                    problems,
                    usedKeys);
        }

        Set<String> unusedMappings = new TreeSet<>(ENUM_BY_COMPARED_EXPRESSION.keySet());
        unusedMappings.removeAll(usedKeys);
        assertThat(problems)
                .as("enum 상수 이름을 바꿨다면 생성 컬럼·트리거도 새 마이그레이션으로 다시 정의해야 한다. 새 비교는 위 표에 등록한다.")
                .isEmpty();
        assertThat(unusedMappings).as("스키마에서 더는 쓰이지 않는 대응 항목").isEmpty();
    }

    private static void scan(String source, String table, String rawSql, List<String> problems, Set<String> usedKeys) {
        String sql = normalize(rawSql);
        Set<Integer> classified = new HashSet<>();

        Matcher comparison = COMPARISON.matcher(sql);
        while (comparison.find()) {
            classified.add(comparison.start(2) - 1);
            check(source, table, comparison.group(1), comparison.group(2), problems, usedKeys);
        }
        Matcher inList = IN_LIST.matcher(sql);
        while (inList.find()) {
            Matcher literals = STRING_LITERAL.matcher(inList.group(2));
            while (literals.find()) {
                classified.add(inList.start(2) + literals.start());
                check(source, table, inList.group(1), literals.group(1), problems, usedKeys);
            }
        }

        Matcher literal = STRING_LITERAL.matcher(sql);
        while (literal.find()) {
            if (!classified.contains(literal.start())) {
                problems.add("%s: '%s' 이(가) '열 = 리터럴'이나 'IN (...)' 비교가 아닌 자리에 있어 enum과 대조하지 못했다"
                        .formatted(source, literal.group(1)));
            }
        }
    }

    // information_schema는 생성 식을 _utf8mb4\'X\' 꼴로 돌려주므로 문자셋 표시, 이스케이프, 백틱을 걷어낸다.
    private static String normalize(String sql) {
        return CHARSET_INTRODUCER
                .matcher(sql)
                .replaceAll("")
                .replace("\\'", "'")
                .replace("`", "");
    }

    private static void check(
            String source,
            String table,
            String comparedExpression,
            String literal,
            List<String> problems,
            Set<String> usedKeys) {
        String key = table + ":" + comparedExpression.toLowerCase();
        Class<? extends Enum<?>> enumType = ENUM_BY_COMPARED_EXPRESSION.get(key);
        if (enumType == null) {
            problems.add("%s: '%s' 와 비교하는 %s 이(가) 대응 표에 없다(키 %s)".formatted(source, literal, comparedExpression, key));
            return;
        }
        usedKeys.add(key);
        boolean known = Arrays.stream(enumType.getEnumConstants())
                .anyMatch(constant -> constant.name().equals(literal));
        if (!known) {
            problems.add("%s: '%s' 은(는) %s 의 상수가 아니다. 허용 값: %s"
                    .formatted(
                            source, literal, enumType.getSimpleName(), Arrays.toString(enumType.getEnumConstants())));
        }
    }
}
