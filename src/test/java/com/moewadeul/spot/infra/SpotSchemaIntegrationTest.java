package com.moewadeul.spot.infra;

import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.SpotFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotSchemaIntegrationTest {

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[ADR-001] 마이그레이션을 적용하면 장소·공공데이터 테이블이 모두 만들어진다")
    void spotAndPublicDataTablesExistAfterMigration() {
        // given

        // when
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.tables"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'",
                String.class);

        // then
        assertThat(tableNames)
                .containsAll(List.of(
                        "protected_area",
                        "spot",
                        "public_spot_detail",
                        "bakji_detail",
                        "bakji_confirmation",
                        "bakji_report",
                        "sync_job_run"));
    }

    @Test
    @DisplayName("[ADR-001] 장소 좌표와 공원 경계에 공간 인덱스가 있다")
    void spotLocationAndProtectedAreaBoundaryHaveSpatialIndexes() {
        // given

        // when
        String spotIndexType = indexTypeOf("spot", "location");
        String boundaryIndexType = indexTypeOf("protected_area", "boundary");

        // then
        assertThat(spotIndexType).isEqualTo("SPATIAL");
        assertThat(boundaryIndexType).isEqualTo("SPATIAL");
    }

    @Test
    @DisplayName("[F-03] 장소를 저장하거나 좌표를 바꾸면 MySQL이 위도·경도 컬럼을 좌표와 같은 값으로 채운다")
    void latitudeAndLongitudeColumnsFollowLocation() {
        // given
        long spotId =
                spotFixture.insert(aSpot().type("CAMPSITE").name("좌표 확인 야영장").at(37.5665, 126.978));

        // when
        Map<String, Object> saved = latitudeAndLongitudeOf(spotId);
        jdbcTemplate.update(
                "UPDATE spot SET location = ST_GeomFromText('POINT(35.1796 129.0756)', 4326) WHERE id = ?", spotId);
        Map<String, Object> moved = latitudeAndLongitudeOf(spotId);

        // then
        assertThat(saved).containsEntry("latitude", 37.5665).containsEntry("longitude", 126.978);
        assertThat(moved).containsEntry("latitude", 35.1796).containsEntry("longitude", 129.0756);
    }

    @Test
    @DisplayName("[ADR-001] SRID가 4326이 아닌 좌표로 장소를 저장할 수 없다")
    void spotLocationRejectsPointWithoutSrid4326() {
        // given
        String sql = "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('CAMPSITE', '좌표계 없는 장소', ST_GeomFromText('POINT(1 1)'), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))";

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(sql)).isInstanceOf(DataAccessException.class);
        assertThat(count("spot")).isZero();
    }

    @Test
    @DisplayName("[ADR-001] 좌표 없이 장소를 저장할 수 없다")
    void spotLocationMustNotBeNull() {
        // given
        String sql = "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('CAMPSITE', '좌표 없는 장소', NULL, 60, 127, 'ACTIVE', NOW(6), NOW(6))";

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("spot")).isZero();
    }

    @Test
    @DisplayName("[ADR-001] 공원 경계를 SRID 4326 멀티폴리곤으로 저장하고 장소가 경계를 참조할 수 있다")
    void spotCanReferenceProtectedArea() {
        // given
        long protectedAreaId = insertProtectedArea("북한산국립공원");

        // when
        long spotId = spotFixture.insert(aSpot().name("경계 안 박지"));
        jdbcTemplate.update(
                "UPDATE spot SET park_warning = TRUE, protected_area_id = ? WHERE id = ?", protectedAreaId, spotId);

        // then
        Long referenced =
                jdbcTemplate.queryForObject("SELECT protected_area_id FROM spot WHERE id = ?", Long.class, spotId);
        assertThat(referenced).isEqualTo(protectedAreaId);
    }

    @Test
    @DisplayName("[F-06] 같은 출처의 같은 외부 ID로 공공 장소 상세를 두 번 저장할 수 없다")
    void publicSpotDetailSourceAndExternalIdMustBeUnique() {
        // given
        long firstSpotId = spotFixture.insert(aSpot().type("CAMPSITE").name("첫 번째 야영장"));
        long secondSpotId = spotFixture.insert(aSpot().type("CAMPSITE").name("두 번째 야영장"));
        insertPublicSpotDetail(firstSpotId, "GOCAMPING", "100");

        // when & then
        assertThatThrownBy(() -> insertPublicSpotDetail(secondSpotId, "GOCAMPING", "100"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("[F-06] 출처가 다르면 같은 외부 ID로 공공 장소 상세를 저장할 수 있다")
    void publicSpotDetailAllowsSameExternalIdFromDifferentSource() {
        // given
        long campsiteId = spotFixture.insert(aSpot().type("CAMPSITE").name("야영장"));
        long forestId = spotFixture.insert(aSpot().type("FOREST").name("자연휴양림"));
        insertPublicSpotDetail(campsiteId, "GOCAMPING", "100");

        // when
        insertPublicSpotDetail(forestId, "FOREST", "100");

        // then
        assertThat(count("public_spot_detail")).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-08] 한 회원은 같은 박지를 한 번만 신고할 수 있다")
    void memberCanReportSameBakjiOnlyOnce() {
        // given
        long memberId = insertMember("reporter");
        long spotId = spotFixture.insert(aSpot().name("신고할 박지"));
        insertBakjiReport(spotId, memberId);

        // when & then
        assertThatThrownBy(() -> insertBakjiReport(spotId, memberId)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("[F-08] 한 회원은 같은 박지를 한 번만 확인할 수 있다")
    void memberCanConfirmSameBakjiOnlyOnce() {
        // given
        long memberId = insertMember("confirmer");
        long spotId = spotFixture.insert(aSpot().name("확인할 박지"));
        insertBakjiConfirmation(spotId, memberId);

        // when & then
        assertThatThrownBy(() -> insertBakjiConfirmation(spotId, memberId)).isInstanceOf(DuplicateKeyException.class);
    }

    private Map<String, Object> latitudeAndLongitudeOf(long spotId) {
        return jdbcTemplate.queryForMap("SELECT latitude, longitude FROM spot WHERE id = ?", spotId);
    }

    private String indexTypeOf(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT INDEX_TYPE FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                String.class,
                table,
                column);
    }

    private long insertMember(String nickname) {
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'USER', NOW(6), NOW(6))",
                nickname + "@example.com",
                nickname);
        return jdbcTemplate.queryForObject("SELECT id FROM member WHERE nickname = ?", Long.class, nickname);
    }

    private long insertProtectedArea(String name) {
        jdbcTemplate.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES (?, 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText('MULTIPOLYGON(((37 126, 37 127, 38 127, 38 126, 37 126)))', 4326),"
                        + " NOW(6), NOW(6))",
                name);
        return jdbcTemplate.queryForObject("SELECT id FROM protected_area WHERE name = ?", Long.class, name);
    }

    private void insertPublicSpotDetail(long spotId, String source, String externalId) {
        jdbcTemplate.update(
                "INSERT INTO public_spot_detail (spot_id, source, external_id, synced_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
                spotId,
                source,
                externalId);
    }

    private void insertBakjiReport(long spotId, long reporterId) {
        jdbcTemplate.update(
                "INSERT INTO bakji_report (spot_id, reporter_id, reason, created_at)"
                        + " VALUES (?, ?, 'FALSE_INFO', NOW(6))",
                spotId,
                reporterId);
    }

    private void insertBakjiConfirmation(long spotId, long memberId) {
        jdbcTemplate.update(
                "INSERT INTO bakji_confirmation (spot_id, member_id, created_at) VALUES (?, ?, NOW(6))",
                spotId,
                memberId);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count;
    }
}
