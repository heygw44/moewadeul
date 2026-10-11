package com.moewadeul.common.testsupport;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * 호출하면 현재 스키마의 모든 테이블 행을 지운다. 테이블 목록은 information_schema에서 읽으므로,
 * 마이그레이션이 늘어도 이 클래스를 고칠 필요가 없다.
 */
public final class DatabaseCleaner {

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";
    private static final String FIND_TABLES = "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'";

    private final DataSource dataSource;

    public DatabaseCleaner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void clean() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            deleteAllRows(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("테스트 DB 정리에 실패했다", e);
        }
    }

    private void deleteAllRows(Statement statement) throws SQLException {
        List<String> tables = findTables(statement);
        // 부모·자식 삭제 순서를 직접 계산하지 않으려고, 우리는 FK(외래 키) 검사를 끈다.
        // 이 세션 변수는 커넥션 풀의 물리 연결에 남으므로, 정리가 끝나면 반드시 되돌린다.
        statement.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            // 트리거가 있는 테이블의 행을 지우면, MySQL이 다른 테이블(지도 묶음 집계 등)에 행을 다시 쓸 수 있다.
            // 그 테이블을 이미 비운 뒤라면 행이 남으므로, 첫 번째에 지운 뒤 한 번 더 지운다.
            // 두 번째에는 트리거를 부를 행이 남아 있지 않다.
            deleteFrom(statement, tables);
            deleteFrom(statement, tables);
        } finally {
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private void deleteFrom(Statement statement, List<String> tables) throws SQLException {
        for (String table : tables) {
            // DELETE를 쓰고 TRUNCATE는 쓰지 않는다.
            // TRUNCATE는 DDL(테이블 구조를 다루는 명령)이라 암묵적 커밋이 일어나고,
            // 테이블이 많으면 테스트마다 훨씬 느려지기 때문이다.
            statement.executeUpdate("DELETE FROM `" + table + "`");
        }
    }

    private List<String> findTables(Statement statement) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery(FIND_TABLES)) {
            while (resultSet.next()) {
                addIfNotFlywayHistory(tables, resultSet.getString(1));
            }
        }
        return tables;
    }

    // Flyway 이력 테이블은 지우지 않는다.
    // 지우면 Flyway가 다음 컨텍스트에서 마이그레이션을 처음부터 다시 적용하려 해서 실패한다.
    private void addIfNotFlywayHistory(List<String> tables, String table) {
        if (FLYWAY_HISTORY_TABLE.equalsIgnoreCase(table)) {
            return;
        }
        tables.add(table);
    }
}
