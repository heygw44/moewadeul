package com.moewadeul.spot.infra.map;

import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MemberFixture;
import com.moewadeul.common.testsupport.SpotFixture;
import com.moewadeul.member.domain.MemberStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

@IntegrationTest
class SpotClusterCellTriggerIntegrationTest {

    private static final String ACTIVE_SPOTS = "SELECT s.latitude, s.longitude, s.type,"
            + " COALESCE(b.has_water, FALSE) AS has_water, COALESCE(b.has_toilet, FALSE) AS has_toilet, s.park_warning"
            + " FROM spot s LEFT JOIN bakji_detail b ON b.spot_id = s.id WHERE s.status = 'ACTIVE'";
    private static final String ACTUAL_TOTAL =
            "SELECT grid_level, cell_row, cell_col, spot_count, lat_sum, lng_sum FROM spot_cluster_total";
    private static final String ACTUAL_CELL = "SELECT grid_level, cell_row, cell_col, type, has_water, has_toilet,"
            + " park_warning, spot_count, lat_sum, lng_sum FROM spot_cluster_cell";

    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MySQLContainer mysql;

    @Test
    @DisplayName("[F-03] 장소를 넣으면 트리거가 8단계 격자 칸마다 장소 수와 위도·경도 합을 더한다")
    void insertAddsSpotToEveryGridLevel() {
        // given & when
        spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(37.5665, 126.978));
        spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(37.5701, 126.9832));
        spotFixture.insert(aSpot().type("CAMPSITE").status("HIDDEN").at(37.5665, 126.978));

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
        long memberId =
                memberFixture.save(aMember().status(MemberStatus.ACTIVE)).getId();
        long campsiteId =
                spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(37.5665, 126.978));
        long forestId = spotFixture.insert(
                aSpot().type("FOREST").status("ACTIVE").parkWarning(true).at(37.71, 128.75));
        long bakjiId = spotFixture.insert(aSpot().type("BAKJI").status("ACTIVE").at(37.72, 128.76));
        insertBakjiDetail(bakjiId, memberId, true, false);
        long hiddenBakjiId = spotFixture.insert(
                aSpot().type("BAKJI").status("PENDING_REVIEW").at(35.1796, 129.0756));
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
        spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(37.5665, 126.978));
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

    @Test
    @DisplayName("[F-03] 음수 좌표와 칸 경계 위·근처의 장소도 서버 격자 공식이 정한 단계·칸에만 센다")
    void triggersUseSameGridAsServerAtNegativeAndBoundaryCoordinates() {
        // given
        double coarsest = SpotGridCells.cellSize(SpotGridCells.LEVEL_COUNT - 1);

        // when
        spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(-33.8688, -70.6693));
        spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(-0.00001, 0.00001));
        spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(0.0, 0.0));
        spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(37.08, 126.82));
        spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(37.08 - 1e-9, 126.82 + 1e-9));
        spotFixture.insert(aSpot().type("FOREST").status("ACTIVE").at(-37.84, -70.68));
        spotFixture.insert(aSpot().type("FOREST").status("ACTIVE").at(100 * coarsest, -100 * coarsest));

        // then
        assertTablesMatchSpots();
    }

    @Test
    @DisplayName("[F-03] 유형이나 경고만 바뀌면 필터 없는 집계(spot_cluster_total)는 그대로이고 필터 집계(spot_cluster_cell)만 칸 사이를 옮긴다")
    void typeOrParkWarningOnlyChangeKeepsTotalAndMovesCell() {
        // given
        long spotId =
                spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(37.5665, 126.978));
        List<Map<String, Object>> totalBefore = jdbcTemplate.queryForList(ACTUAL_TOTAL);

        // when
        jdbcTemplate.update("UPDATE spot SET park_warning = TRUE WHERE id = ?", spotId);

        // then
        assertThat(jdbcTemplate.queryForList(ACTUAL_TOTAL)).containsExactlyInAnyOrderElementsOf(totalBefore);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM spot_cluster_cell WHERE park_warning = TRUE AND grid_level = 0",
                        Integer.class))
                .isEqualTo(1);
        assertTablesMatchSpots();

        // when
        jdbcTemplate.update("UPDATE spot SET type = 'FOREST' WHERE id = ?", spotId);

        // then
        assertThat(jdbcTemplate.queryForList(ACTUAL_TOTAL)).containsExactlyInAnyOrderElementsOf(totalBefore);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM spot_cluster_cell WHERE type = 'FOREST' AND grid_level = 0",
                        Integer.class))
                .isEqualTo(1);
        assertTablesMatchSpots();
    }

    @Test
    @DisplayName("[F-03] 다른 트랜잭션이 경고만 바꾸고 커밋하지 않았어도, 같은 칸에 다른 유형의 장소를 넣는 INSERT는 기다리지 않는다")
    void insertIsNotBlockedByUncommittedParkWarningOnlyUpdate() throws Exception {
        // given
        long spotId =
                spotFixture.insert(aSpot().type("CAMPSITE").status("ACTIVE").at(37.5665, 126.978));
        try (Connection rejudge = dataSource.getConnection();
                Connection sync = dataSource.getConnection()) {
            rejudge.setAutoCommit(false);
            try (PreparedStatement update =
                    rejudge.prepareStatement("UPDATE spot SET park_warning = TRUE WHERE id = ?")) {
                update.setLong(1, spotId);
                update.executeUpdate();
            }
            execute(sync, "SET SESSION innodb_lock_wait_timeout = 2");

            // when & then
            try {
                assertThatCode(() -> insertSpot(sync, "FOREST", 37.5665, 126.978))
                        .doesNotThrowAnyException();
            } finally {
                execute(sync, "SET SESSION innodb_lock_wait_timeout = DEFAULT");
                rejudge.rollback();
            }
        }
    }

    @Test
    @DisplayName("[F-03] 박지 제보 둘이 장소와 상세를 번갈아 넣어도 교착 없이 둘 다 커밋된다")
    void concurrentBakjiReportsCommitWithoutDeadlock() throws Exception {
        // given
        long reporterId =
                memberFixture.save(aMember().status(MemberStatus.ACTIVE)).getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection first = dataSource.getConnection();
                Connection second = dataSource.getConnection();
                Connection monitor = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword())) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            execute(first, "SET SESSION innodb_lock_wait_timeout = 5");
            execute(second, "SET SESSION innodb_lock_wait_timeout = 5");
            long firstSpotId = insertSpot(first, "BAKJI", 37.5665, 126.978);
            long secondSpotId = insertSpot(second, "BAKJI", 35.1796, 129.0756);

            // when
            Future<?> firstDetail = executor.submit(() -> insertBakjiDetail(first, firstSpotId, reporterId));
            awaitLockWaitOrDone(monitor, firstDetail);
            Future<?> secondDetail = executor.submit(() -> insertBakjiDetail(second, secondSpotId, reporterId));

            // then
            try {
                assertThatCode(() -> firstDetail.get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();
                assertThatCode(() -> secondDetail.get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();
                first.commit();
                second.commit();
            } finally {
                execute(first, "SET SESSION innodb_lock_wait_timeout = DEFAULT");
                execute(second, "SET SESSION innodb_lock_wait_timeout = DEFAULT");
                first.rollback();
                second.rollback();
                executor.shutdownNow();
            }
        }
        assertTablesMatchSpots();
    }

    private long insertSpot(Connection connection, String type, double latitude, double longitude) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, park_warning, status, created_at,"
                        + " updated_at) VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, FALSE, 'ACTIVE', NOW(6),"
                        + " NOW(6))",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, type);
            statement.setString(2, type + latitude);
            statement.setString(3, "POINT(" + latitude + " " + longitude + ")");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private int insertBakjiDetail(Connection connection, long spotId, long reporterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, TRUE, FALSE, NOW(6), NOW(6))")) {
            statement.setLong(1, spotId);
            statement.setLong(2, reporterId);
            return statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void awaitLockWaitOrDone(Connection monitor, Future<?> future) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!future.isDone() && System.nanoTime() < deadline) {
            try (Statement statement = monitor.createStatement();
                    ResultSet waits =
                            statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
                waits.next();
                if (waits.getInt(1) > 0) {
                    return;
                }
            }
            Thread.sleep(20);
        }
    }

    private void assertTablesMatchSpots() {
        Map<List<Object>, Aggregate> expectedTotal = new HashMap<>();
        Map<List<Object>, Aggregate> expectedCell = new HashMap<>();
        for (Map<String, Object> spot : jdbcTemplate.queryForList(ACTIVE_SPOTS)) {
            double lat = ((Number) spot.get("latitude")).doubleValue();
            double lng = ((Number) spot.get("longitude")).doubleValue();
            Aggregate one = new Aggregate(1, lat, lng);
            for (int level = 0; level < SpotGridCells.LEVEL_COUNT; level++) {
                double size = SpotGridCells.cellSize(level);
                long cellRow = SpotGridCells.cellIndex(lat, size);
                long cellCol = SpotGridCells.cellIndex(lng, size);
                List<Object> cell = List.of((long) level, cellRow, cellCol);
                expectedTotal.merge(cell, one, Aggregate::plus);
                List<Object> cellWithAttributes = new ArrayList<>(cell);
                Stream.of("type", "has_water", "has_toilet", "park_warning")
                        .map(column -> comparable(spot.get(column)))
                        .forEach(cellWithAttributes::add);
                expectedCell.merge(cellWithAttributes, one, Aggregate::plus);
            }
        }
        assertSameAggregates(aggregates(ACTUAL_TOTAL, 3), expectedTotal);
        assertSameAggregates(aggregates(ACTUAL_CELL, 7), expectedCell);
    }

    private Map<List<Object>, Aggregate> aggregates(String sql, int keyColumns) {
        Map<List<Object>, Aggregate> aggregates = new HashMap<>();
        jdbcTemplate.query(sql, row -> {
            List<Object> key = new ArrayList<>();
            for (int column = 1; column <= keyColumns; column++) {
                key.add(comparable(row.getObject(column)));
            }
            aggregates.put(
                    key, new Aggregate(row.getLong("spot_count"), row.getDouble("lat_sum"), row.getDouble("lng_sum")));
        });
        return aggregates;
    }

    private static void assertSameAggregates(
            Map<List<Object>, Aggregate> actual, Map<List<Object>, Aggregate> expected) {
        assertThat(actual.keySet()).isEqualTo(expected.keySet());
        expected.forEach((key, aggregate) -> {
            Aggregate actualAggregate = actual.get(key);
            assertThat(actualAggregate.spotCount()).as("%s spot_count", key).isEqualTo(aggregate.spotCount());
            assertThat(actualAggregate.latSum()).as("%s lat_sum", key).isCloseTo(aggregate.latSum(), within(1e-9));
            assertThat(actualAggregate.lngSum()).as("%s lng_sum", key).isCloseTo(aggregate.lngSum(), within(1e-9));
        });
    }

    private static Object comparable(Object value) {
        if (value instanceof Boolean flag) {
            return flag ? 1L : 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return String.valueOf(value);
    }

    private record Aggregate(long spotCount, double latSum, double lngSum) {

        Aggregate plus(Aggregate other) {
            return new Aggregate(spotCount + other.spotCount, latSum + other.latSum, lngSum + other.lngSum);
        }
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
}
