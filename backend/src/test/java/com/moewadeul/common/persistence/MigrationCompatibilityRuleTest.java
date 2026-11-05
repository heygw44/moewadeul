package com.moewadeul.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigrationCompatibilityRuleTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final int LAST_VERSION_BEFORE_RULE = 66;
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern CONTRACT_MARK = Pattern.compile("(?m)^--\\s*contract:\\s*\\S");

    private static final Pattern ALTER_TABLE = Pattern.compile("(?is)^\\s*ALTER\\s+TABLE\\b.*");
    private static final Pattern DROP_TABLE = Pattern.compile("(?is)^\\s*DROP\\s+TABLE\\b.*");
    private static final Pattern RENAME_TABLE = Pattern.compile("(?is)^\\s*RENAME\\s+TABLE\\b.*");
    private static final Pattern DROP_COLUMN =
            Pattern.compile("(?i)\\bDROP\\s+(?!INDEX\\b|KEY\\b|FOREIGN\\b|PRIMARY\\b|CHECK\\b|CONSTRAINT\\b)");
    private static final Pattern RENAME_OR_CHANGE = Pattern.compile("(?i)\\b(RENAME\\s+(COLUMN|TO|AS)|CHANGE)\\b");
    private static final Pattern MODIFY_NOT_NULL = Pattern.compile("(?is)\\bMODIFY\\b[^,]*\\bNOT\\s+NULL\\b");
    private static final Pattern ADD_COLUMN = Pattern.compile("(?is)\\bADD\\s+(COLUMN\\s+)?(?!INDEX\\b|KEY\\b|UNIQUE\\b"
            + "|CONSTRAINT\\b|FOREIGN\\b|PRIMARY\\b|SPATIAL\\b|FULLTEXT\\b|CHECK\\b)([^,]*)");
    private static final Pattern NOT_NULL = Pattern.compile("(?i)\\bNOT\\s+NULL\\b");
    private static final Pattern HAS_VALUE_FOR_OLD_WRITERS = Pattern.compile("(?i)\\b(DEFAULT|AS\\s*\\(|GENERATED)\\b");

    @Test
    @DisplayName("[NFR-09] 직전 릴리스 앱이 함께 돌 수 없는 새 마이그레이션은 contract 표시와 이유를 적어야 한다")
    void newMigrationsKeepPreviousReleaseWorking() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(path -> version(path) > LAST_VERSION_BEFORE_RULE).toList()) {
                String sql = Files.readString(file);
                if (!CONTRACT_MARK.matcher(sql).find()) {
                    breakingChanges(sql).forEach(change -> violations.add(file.getFileName() + ": " + change));
                }
            }
        }

        assertThat(violations)
                .as("옛 앱을 깨는 변경은 두 릴리스로 나누고 마지막 단계에 '-- contract: 이유'를 적는다(backend/MIGRATIONS.md)")
                .isEmpty();
    }

    @Test
    @DisplayName("[NFR-09] 옛 앱의 합류 신청을 모두 500으로 만든 V50과 컬럼을 지운 V28을 위반으로 잡는다")
    void detectsPastBreakingMigrations() throws IOException {
        assertThat(breakingChanges(Files.readString(MIGRATIONS.resolve("V50__basecamp_application_applied_at.sql"))))
                .containsExactly("기존 컬럼을 NOT NULL로 변경");
        assertThat(breakingChanges(Files.readString(MIGRATIONS.resolve("V28__drop_community_post_category.sql"))))
                .containsExactly("컬럼 삭제");
    }

    @Test
    @DisplayName("[NFR-09] 기본값 있는 컬럼 추가, 인덱스 변경, 새 표는 옛 앱과 함께 돌 수 있어 통과한다")
    void allowsExpandOnlyChanges() {
        assertThat(breakingChanges("""
                        -- 설명에 DROP COLUMN이 적혀 있어도 주석은 보지 않는다.
                        CREATE TABLE sample (id BIGINT NOT NULL PRIMARY KEY);
                        ALTER TABLE sample ADD COLUMN a INT NOT NULL DEFAULT 0, ADD COLUMN b INT NULL;
                        ALTER TABLE sample ADD COLUMN c DOUBLE AS (a + 1) STORED NOT NULL;
                        ALTER TABLE sample ADD INDEX idx_a (a), DROP INDEX idx_b, DROP FOREIGN KEY fk_c;
                        UPDATE sample SET a = 1;
                        """))
                .isEmpty();
    }

    @Test
    @DisplayName("[NFR-09] 기본값 없는 NOT NULL 추가, 이름 변경, 표 삭제를 위반으로 잡는다")
    void detectsEachBreakingShape() {
        assertThat(breakingChanges("ALTER TABLE sample ADD COLUMN a INT NOT NULL;"))
                .containsExactly("기본값 없는 NOT NULL 컬럼 추가");
        assertThat(breakingChanges("ALTER TABLE sample RENAME COLUMN a TO b;")).containsExactly("이름 변경");
        assertThat(breakingChanges("ALTER TABLE sample CHANGE a b INT NULL;")).containsExactly("이름 변경");
        assertThat(breakingChanges("RENAME TABLE sample TO other;")).containsExactly("표 이름 변경");
        assertThat(breakingChanges("DROP TABLE sample;")).containsExactly("표 삭제");
        assertThat(breakingChanges("ALTER TABLE sample DROP a;")).containsExactly("컬럼 삭제");
    }

    private static int version(Path file) {
        Matcher matcher = VERSION.matcher(file.getFileName().toString());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static List<String> breakingChanges(String sql) {
        String withoutComments = sql.replaceAll("(?m)--.*$", "");
        return Arrays.stream(withoutComments.split(";"))
                .flatMap(statement -> breakingChangesIn(statement).stream())
                .toList();
    }

    private static List<String> breakingChangesIn(String statement) {
        if (DROP_TABLE.matcher(statement).matches()) {
            return List.of("표 삭제");
        }
        if (RENAME_TABLE.matcher(statement).matches()) {
            return List.of("표 이름 변경");
        }
        if (!ALTER_TABLE.matcher(statement).matches()) {
            return List.of();
        }
        List<String> changes = new ArrayList<>();
        if (DROP_COLUMN.matcher(statement).find()) {
            changes.add("컬럼 삭제");
        }
        if (RENAME_OR_CHANGE.matcher(statement).find()) {
            changes.add("이름 변경");
        }
        if (MODIFY_NOT_NULL.matcher(statement).find()) {
            changes.add("기존 컬럼을 NOT NULL로 변경");
        }
        Matcher added = ADD_COLUMN.matcher(statement);
        while (added.find()) {
            String definition = added.group(2);
            if (NOT_NULL.matcher(definition).find() && !HAS_VALUE_FOR_OLD_WRITERS.matcher(definition).find()) {
                changes.add("기본값 없는 NOT NULL 컬럼 추가");
            }
        }
        return changes;
    }
}
