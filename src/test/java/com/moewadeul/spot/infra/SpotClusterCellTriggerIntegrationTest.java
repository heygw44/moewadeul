package com.moewadeul.spot.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotClusterCellTriggerIntegrationTest {

    private static final String LEVELS =
            "(SELECT 0 AS grid_level UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3"
                    + " UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) g";
    private static final String EXPECTED_TOTAL = "SELECT g.grid_level,"
            + " FLOOR(s.latitude / (0.0025 * POW(2, g.grid_level))) AS cell_row,"
            + " FLOOR(s.longitude / (0.0025 * POW(2, g.grid_level))) AS cell_col,"
            + " COUNT(*) AS spot_count, SUM(s.latitude) AS lat_sum, SUM(s.longitude) AS lng_sum"
            + " FROM spot s CROSS JOIN " + LEVELS + " WHERE s.status = 'ACTIVE'"
            + " GROUP BY 1, 2, 3 ORDER BY 1, 2, 3";
    private static final String ACTUAL_TOTAL = "SELECT grid_level, cell_row, cell_col, spot_count, lat_sum, lng_sum"
            + " FROM spot_cluster_total ORDER BY 1, 2, 3";
    private static final String EXPECTED_CELL = "SELECT g.grid_level,"
            + " FLOOR(s.latitude / (0.0025 * POW(2, g.grid_level))) AS cell_row,"
            + " FLOOR(s.longitude / (0.0025 * POW(2, g.grid_level))) AS cell_col,"
            + " s.type, COALESCE(b.has_water, FALSE) AS has_water, COALESCE(b.has_toilet, FALSE) AS has_toilet,"
            + " s.park_warning, COUNT(*) AS spot_count, SUM(s.latitude) AS lat_sum, SUM(s.longitude) AS lng_sum"
            + " FROM spot s LEFT JOIN bakji_detail b ON b.spot_id = s.id CROSS JOIN " + LEVELS
            + " WHERE s.status = 'ACTIVE' GROUP BY 1, 2, 3, 4, 5, 6, 7 ORDER BY 1, 2, 3, 4, 5, 6, 7";
    private static final String ACTUAL_CELL = "SELECT grid_level, cell_row, cell_col, type, has_water, has_toilet,"
            + " park_warning, spot_count, lat_sum, lng_sum FROM spot_cluster_cell ORDER BY 1, 2, 3, 4, 5, 6, 7";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[F-03] 장소를 넣으면 트리거가 8단계 격자 칸마다 장소 수와 위도·경도 합을 더한다")
    void insertAddsSpotToEveryGridLevel() {
        // given & when
        insertSpot("CAMPSITE", "ACTIVE", false, 37.5665, 126.978);
        insertSpot("CAMPSITE", "ACTIVE", false, 37.5701, 126.9832);
        insertSpot("CAMPSITE", "HIDDEN", false, 37.5665, 126.978);

        // then
        List<Map<String, Object>> coarsest =
                jdbcTemplate.queryForList("SELECT spot_count, lat_sum FROM spot_cluster_total WHERE grid_level = 7");
        assertThat(coarsest).hasSize(1);
        assertThat(((Number) coarsest.getFirst().get("spot_count")).intValue()).isEqualTo(2);
        assertThat((Double) coarsest.getFirst().get("lat_sum")).isCloseTo(37.5665 + 37.5701, within(1e-9));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM spot_cluster_total WHERE grid_level = 0", Integer.class))
                .isEqualTo(2);
        assertTablesMatchSpots();
    }

    @Test
    @DisplayName("[F-03] 숨김·복구, 좌표 이동, 경고·시설 변경, 삭제를 거쳐도 두 집계 표가 장소 테이블과 같다")
    void aggregatesFollowEveryKindOfSpotChange() {
        // given
        long memberId = insertMember();
        long campsiteId = insertSpot("CAMPSITE", "ACTIVE", false, 37.5665, 126.978);
        long forestId = insertSpot("FOREST", "ACTIVE", true, 37.71, 128.75);
        long bakjiId = insertSpot("BAKJI", "ACTIVE", false, 37.72, 128.76);
        insertBakjiDetail(bakjiId, memberId, true, false);
        long hiddenBakjiId = insertSpot("BAKJI", "PENDING_REVIEW", false, 35.1796, 129.0756);
        insertBakjiDetail(hiddenBakjiId, memberId, false, true);
        assertTablesMatchSpots();

        // when
        jdbcTemplate.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", campsiteId);
        assertTablesMatchSpots();
        jdbcTemplate.update("UPDATE spot SET status = 'ACTIVE' WHERE id = ?", campsiteId);
        jdbcTemplate.update("UPDATE spot SET status = 'ACTIVE' WHERE id = ?", hiddenBakjiId);
        assertTablesMatchSpots();
        jdbcTemplate.update(
                "UPDATE spot SET location = ST_GeomFromText('POINT(33.38 126.55)', 4326) WHERE id = ?", bakjiId);
        jdbcTemplate.update("UPDATE spot SET park_warning = FALSE WHERE id = ?", forestId);
        jdbcTemplate.update("UPDATE bakji_detail SET has_water = FALSE, has_toilet = TRUE WHERE spot_id = ?", bakjiId);
        jdbcTemplate.update("UPDATE spot SET name = ?, updated_at = NOW(6) WHERE id = ?", "이름만 바꾼 장소", forestId);
        assertTablesMatchSpots();
        jdbcTemplate.update("DELETE FROM bakji_detail WHERE spot_id = ?", hiddenBakjiId);
        jdbcTemplate.update("DELETE FROM spot WHERE id = ?", hiddenBakjiId);

        // then
        assertTablesMatchSpots();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM spot_cluster_total WHERE spot_count <= 0", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[F-03] 집계 표를 먼저 비운 뒤 장소를 지워도 음수 행이 남지 않는다")
    void deletingSpotsAfterAggregatesAreClearedLeavesNoNegativeRows() {
        // given
        insertSpot("CAMPSITE", "ACTIVE", false, 37.5665, 126.978);
        jdbcTemplate.update("DELETE FROM spot_cluster_total");
        jdbcTemplate.update("DELETE FROM spot_cluster_cell");

        // when
        jdbcTemplate.update("DELETE FROM spot");

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM spot_cluster_total", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM spot_cluster_cell", Integer.class))
                .isZero();
    }

    private void assertTablesMatchSpots() {
        assertSameRows(jdbcTemplate.queryForList(ACTUAL_TOTAL), jdbcTemplate.queryForList(EXPECTED_TOTAL));
        assertSameRows(jdbcTemplate.queryForList(ACTUAL_CELL), jdbcTemplate.queryForList(EXPECTED_CELL));
    }

    private static void assertSameRows(List<Map<String, Object>> actual, List<Map<String, Object>> expected) {
        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            Map<String, Object> actualRow = actual.get(i);
            Map<String, Object> expectedRow = expected.get(i);
            for (String column : expectedRow.keySet()) {
                Object expectedValue = expectedRow.get(column);
                Object actualValue = actualRow.get(column);
                if (column.endsWith("_sum")) {
                    assertThat(((Number) actualValue).doubleValue())
                            .as(column)
                            .isCloseTo(((Number) expectedValue).doubleValue(), within(1e-9));
                } else {
                    assertThat(comparable(actualValue)).as(column).isEqualTo(comparable(expectedValue));
                }
            }
        }
    }

    private static Object comparable(Object value) {
        if (value instanceof Boolean flag) {
            return flag ? 1.0 : 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return String.valueOf(value);
    }

    private long insertSpot(String type, String status, boolean parkWarning, double lat, double lng) {
        String name = TestSequence.unique("집계");
        jdbcTemplate.update(
                "INSERT INTO spot"
                        + " (type, name, location, weather_nx, weather_ny, park_warning, status, created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, ?, ?, NOW(6), NOW(6))",
                type,
                name,
                "POINT(" + lat + " " + lng + ")",
                parkWarning,
                status);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private void insertBakjiDetail(long spotId, long reporterId, boolean hasWater, boolean hasToilet) {
        jdbcTemplate.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
                spotId,
                reporterId,
                hasWater,
                hasToilet);
    }

    private long insertMember() {
        String nickname = TestSequence.nickname();
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'USER', NOW(6), NOW(6))",
                TestSequence.email(),
                nickname);
        return jdbcTemplate.queryForObject("SELECT id FROM member WHERE nickname = ?", Long.class, nickname);
    }
}
