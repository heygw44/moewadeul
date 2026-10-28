package com.moewadeul.common.testsupport;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

public final class DatabaseCleaner {

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";
    private static final Set<String> TRIGGER_MAINTAINED_TABLES = Set.of("spot_cluster_total", "spot_cluster_cell");
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
        statement.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            deleteFrom(statement, triggerMaintainedTablesLast(tables));
        } finally {
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private static List<String> triggerMaintainedTablesLast(List<String> tables) {
        return tables.stream()
                .sorted(Comparator.comparing(TRIGGER_MAINTAINED_TABLES::contains))
                .toList();
    }

    private void deleteFrom(Statement statement, List<String> tables) throws SQLException {
        for (String table : tables) {
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

    private void addIfNotFlywayHistory(List<String> tables, String table) {
        if (FLYWAY_HISTORY_TABLE.equalsIgnoreCase(table)) {
            return;
        }
        tables.add(table);
    }
}
