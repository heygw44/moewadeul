package com.moewadeul.common.testsupport;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class SpotFixture {

    private final JdbcTemplate jdbc;

    public SpotFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static NewSpot aSpot() {
        return new NewSpot();
    }

    public long insert() {
        return insert(aSpot());
    }

    public long insert(NewSpot spot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO spot (type, name, location, address, weather_nx, weather_ny, park_warning,"
                                    + " protected_area_id, status, created_at, updated_at)"
                                    + " VALUES (?, ?, ST_GeomFromText(?, 4326), ?, 60, 127, ?, ?, ?,"
                                    + " COALESCE(?, NOW(6)), COALESCE(?, NOW(6)))",
                            Statement.RETURN_GENERATED_KEYS);
                    Timestamp createdAt = spot.createdAt == null ? null : Timestamp.from(spot.createdAt);
                    statement.setString(1, spot.type);
                    statement.setString(2, spot.name == null ? TestSequence.unique("장소") : spot.name);
                    statement.setString(3, spot.pointWkt());
                    statement.setString(4, spot.address);
                    statement.setBoolean(5, spot.parkWarning);
                    if (spot.protectedAreaId == null) {
                        statement.setNull(6, Types.BIGINT);
                    } else {
                        statement.setLong(6, spot.protectedAreaId);
                    }
                    statement.setString(7, spot.status);
                    statement.setTimestamp(8, createdAt);
                    statement.setTimestamp(9, createdAt);
                    return statement;
                },
                keyHolder);
        return keyHolder.getKey().longValue();
    }

    public static final class NewSpot {

        private String type = "BAKJI";
        private String name;
        private double latitude = 37.25;
        private double longitude = 127.25;
        private String address;
        private boolean parkWarning;
        private Long protectedAreaId;
        private String status = "ACTIVE";
        private Instant createdAt;

        private NewSpot() {}

        public NewSpot type(String type) {
            this.type = type;
            return this;
        }

        public NewSpot name(String name) {
            this.name = name;
            return this;
        }

        public NewSpot at(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            return this;
        }

        public NewSpot address(String address) {
            this.address = address;
            return this;
        }

        public NewSpot parkWarning(boolean parkWarning) {
            this.parkWarning = parkWarning;
            return this;
        }

        public NewSpot protectedAreaId(long protectedAreaId) {
            this.protectedAreaId = protectedAreaId;
            return this;
        }

        public NewSpot status(String status) {
            this.status = status;
            return this;
        }

        public NewSpot createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        private String pointWkt() {
            return "POINT(" + BigDecimal.valueOf(latitude).toPlainString() + " "
                    + BigDecimal.valueOf(longitude).toPlainString() + ")";
        }
    }
}
