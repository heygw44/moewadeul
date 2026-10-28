package com.moewadeul.spot.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

// 공유 DB는 이미 최신 버전이라 V55 직전 상태를 만들 수 없다. 그래서 같은 컨테이너에 빈 스키마를 따로 만들어 V54까지 올린다.
@IntegrationTest
class ProtectedAreaNaturalKeyMigrationIntegrationTest {

    private static final String SCHEMA = "protected_area_migration";

    @Autowired
    MySQLContainer mysql;

    JdbcTemplate root;
    DriverManagerDataSource dataSource;
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createSchema() {
        root = new JdbcTemplate(dataSourceFor(""));
        root.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        root.execute("CREATE DATABASE " + SCHEMA);
        dataSource = dataSourceFor(SCHEMA);
        jdbcTemplate = new JdbcTemplate(dataSource);
        migrateTo("54");
    }

    @AfterEach
    void dropSchema() {
        root.execute("DROP DATABASE IF EXISTS " + SCHEMA);
    }

    @Test
    @DisplayName("자연 키가 같은 경계가 여러 행이면 가장 작은 id만 남기고, 지운 행을 가리키던 장소는 남은 행을 가리킨다")
    void duplicatesCollapseToSmallestIdAndSpotsFollow() {
        // given
        long kept = insertProtectedArea("KDPA", "NATIONAL_PARK", "지리산");
        long duplicate = insertProtectedArea("KDPA", "NATIONAL_PARK", "지리산");
        long otherType = insertProtectedArea("KDPA", "PROVINCIAL_PARK", "지리산");
        long unrelated = insertProtectedArea("KDPA", "NATIONAL_PARK", "설악산");
        long spotOnDuplicate = insertSpot(duplicate);
        long spotOnKept = insertSpot(kept);
        long spotOnUnrelated = insertSpot(unrelated);

        // when
        migrateTo("55");

        // then
        assertThat(jdbcTemplate.queryForList("SELECT id FROM protected_area ORDER BY id", Long.class))
                .containsExactly(kept, otherType, unrelated);
        assertThat(protectedAreaIdOf(spotOnDuplicate)).isEqualTo(kept);
        assertThat(protectedAreaIdOf(spotOnKept)).isEqualTo(kept);
        assertThat(protectedAreaIdOf(spotOnUnrelated)).isEqualTo(unrelated);
    }

    @Test
    @DisplayName("마이그레이션 뒤에는 출처, 구분, 이름이 같은 경계를 두 번째로 넣으면 DB가 거부한다")
    void secondRowWithSameNaturalKeyIsRejected() {
        // given
        migrateTo("55");
        insertProtectedArea("KDPA", "NATIONAL_PARK", "지리산");

        // when & then
        assertThatThrownBy(() -> insertProtectedArea("KDPA", "NATIONAL_PARK", "지리산"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private DriverManagerDataSource dataSourceFor(String schema) {
        return new DriverManagerDataSource(
                "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(MySQLContainer.MYSQL_PORT) + "/" + schema
                        + "?useSSL=false&allowPublicKeyRetrieval=true",
                "root",
                mysql.getPassword());
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(dataSource).target(version).load().migrate();
    }

    private long insertProtectedArea(String source, String areaType, String name) {
        jdbcTemplate.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES (?, ?, ?, '2026-01-01',"
                        + " ST_GeomFromText('MULTIPOLYGON(((127 37, 127.1 37, 127.1 37.1, 127 37.1, 127 37)))', 4326,"
                        + " 'axis-order=long-lat'), NOW(6), NOW(6))",
                name,
                areaType,
                source);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM protected_area", Long.class);
    }

    private long insertSpot(long protectedAreaId) {
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, park_warning, protected_area_id, status,"
                        + " created_at, updated_at)"
                        + " VALUES ('BAKJI', '야영장', ST_GeomFromText('POINT(37.05 127.05)', 4326), 60, 127, TRUE, ?,"
                        + " 'ACTIVE', NOW(6), NOW(6))",
                protectedAreaId);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM spot", Long.class);
    }

    private long protectedAreaIdOf(long spotId) {
        return jdbcTemplate.queryForObject("SELECT protected_area_id FROM spot WHERE id = ?", Long.class, spotId);
    }
}
