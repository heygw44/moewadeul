package com.moewadeul.common.testsupport;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class ProtectedAreaFixture {

    private final JdbcTemplate jdbc;

    public ProtectedAreaFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static NewProtectedArea aProtectedArea() {
        return new NewProtectedArea();
    }

    public long insert() {
        return insert(aProtectedArea());
    }

    public long insert(NewProtectedArea area) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO protected_area"
                                    + " (name, area_type, source, source_date, boundary, created_at, updated_at)"
                                    + " VALUES (?, ?, ?, ?, ST_GeomFromText(?, 4326, 'axis-order=long-lat'),"
                                    + " NOW(6), NOW(6))",
                            Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, area.name == null ? TestSequence.unique("보호구역") : area.name);
                    statement.setString(2, area.areaType);
                    statement.setString(3, area.source);
                    statement.setObject(4, area.sourceDate);
                    statement.setString(5, area.longLatBoundaryWkt);
                    return statement;
                },
                keyHolder);
        return keyHolder.getKey().longValue();
    }

    public static final class NewProtectedArea {

        private String name;
        private String areaType = "NATIONAL_PARK";
        private String source = "KDPA";
        private LocalDate sourceDate = LocalDate.of(2026, 1, 1);
        private String longLatBoundaryWkt = "MULTIPOLYGON(((127 37, 128 37, 128 38, 127 38, 127 37)))";

        private NewProtectedArea() {}

        public NewProtectedArea name(String name) {
            this.name = name;
            return this;
        }

        public NewProtectedArea areaType(String areaType) {
            this.areaType = areaType;
            return this;
        }

        public NewProtectedArea source(String source) {
            this.source = source;
            return this;
        }

        public NewProtectedArea sourceDate(LocalDate sourceDate) {
            this.sourceDate = sourceDate;
            return this;
        }

        public NewProtectedArea longLatBoundary(String multiPolygonWkt) {
            this.longLatBoundaryWkt = multiPolygonWkt;
            return this;
        }
    }
}
