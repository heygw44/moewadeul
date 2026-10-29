package com.moewadeul.spot.application.park;

import static com.moewadeul.common.testsupport.ProtectedAreaFixture.aProtectedArea;
import static com.moewadeul.common.testsupport.SpotFixture.aSpot;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.ProtectedAreaFixture;
import com.moewadeul.common.testsupport.SpotFixture;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ParkAreaJudgeServiceIntegrationTest {

    // 경계 WKT는 경계 파일과 같이 (경도 위도) 순서로 쓰고, 넣을 때 axis-order=long-lat 옵션을 붙인다.
    private static final String PARK_SQUARE = "MULTIPOLYGON(((127 37, 127.5 37, 127.5 37.5, 127 37.5, 127 37)))";
    private static final GeoPoint INSIDE_PARK = new GeoPoint(37.25, 127.25);
    private static final GeoPoint OUTSIDE_EVERY_AREA = new GeoPoint(37.25, 127.75);

    private static final Instant SAVED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant JUDGED_AT = Instant.parse("2026-10-06T01:00:00Z");

    @Autowired
    private ProtectedAreaFixture protectedAreaFixture;

    @Autowired
    private SpotFixture spotFixture;

    @Autowired
    private ParkAreaJudgeService parkAreaJudgeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-07] 좌표가 공원 경계 안이면 경고 대상으로 판정하고 그 경계의 ID를 돌려준다")
    void judgesPointInsideBoundaryAsWarned() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea().longLatBoundary(PARK_SQUARE));
        clock.setInstant(JUDGED_AT);

        // when
        ParkAreaJudgement judgement = parkAreaJudgeService.judge(INSIDE_PARK);

        // then
        assertThat(judgement.parkWarning()).isTrue();
        assertThat(judgement.protectedAreaId()).isEqualTo(areaId);
        assertThat(judgement.checkedAt()).isEqualTo(JUDGED_AT);
    }

    @Test
    @DisplayName("[F-07] 좌표를 포함하는 공원 경계가 없으면 경고 없이 판정하고 경계 ID는 비운다")
    void judgesPointOutsideEveryBoundaryAsNotWarned() {
        // given
        protectedAreaFixture.insert(aProtectedArea().longLatBoundary(PARK_SQUARE));
        clock.setInstant(JUDGED_AT);

        // when
        ParkAreaJudgement judgement = parkAreaJudgeService.judge(OUTSIDE_EVERY_AREA);

        // then
        assertThat(judgement.parkWarning()).isFalse();
        assertThat(judgement.protectedAreaId()).isNull();
        assertThat(judgement.checkedAt()).isEqualTo(JUDGED_AT);
    }

    @Test
    @DisplayName("[F-07] 여러 조각으로 된 경계에서 두 번째 조각 안의 좌표도 경고 대상으로 판정한다")
    void judgesPointInsideSecondPolygonOfMultiPolygonAsWarned() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea()
                .longLatBoundary("MULTIPOLYGON(((126 35, 126.2 35, 126.2 35.2, 126 35.2, 126 35)),"
                        + " ((128 36, 128.2 36, 128.2 36.2, 128 36.2, 128 36)))"));

        // when
        ParkAreaJudgement judgement = parkAreaJudgeService.judge(new GeoPoint(36.1, 128.1));

        // then
        assertThat(judgement.parkWarning()).isTrue();
        assertThat(judgement.protectedAreaId()).isEqualTo(areaId);
    }

    @Test
    @DisplayName("[F-07] 위도와 경도를 뒤바꾼 좌표는 경계 밖으로 판정한다")
    void judgesSwappedCoordinateAsOutside() {
        // given
        long areaId = protectedAreaFixture.insert(
                aProtectedArea().longLatBoundary("MULTIPOLYGON(((10 20, 11 20, 11 21, 10 21, 10 20)))"));

        // when
        ParkAreaJudgement inside = parkAreaJudgeService.judge(new GeoPoint(20.5, 10.5));
        ParkAreaJudgement swapped = parkAreaJudgeService.judge(new GeoPoint(10.5, 20.5));

        // then
        assertThat(inside.protectedAreaId()).isEqualTo(areaId);
        assertThat(swapped.parkWarning()).isFalse();
        assertThat(swapped.protectedAreaId()).isNull();
    }

    @Test
    @DisplayName("[F-07] 겹친 경계 두 곳에 들어가는 좌표는 ID가 작은 경계로 판정한다")
    void choosesSmallestIdWhenBoundariesOverlap() {
        // given
        long smallerId = protectedAreaFixture.insert(aProtectedArea().longLatBoundary(PARK_SQUARE));
        long largerId = protectedAreaFixture.insert(aProtectedArea()
                .longLatBoundary("MULTIPOLYGON(((126.5 36.5, 128 36.5, 128 38, 126.5 38, 126.5 36.5)))"));

        // when
        ParkAreaJudgement judgement = parkAreaJudgeService.judge(INSIDE_PARK);

        // then
        assertThat(smallerId).isLessThan(largerId);
        assertThat(judgement.protectedAreaId()).isEqualTo(smallerId);
    }

    @Test
    @DisplayName("[F-07] 저장 크기가 큰 경계가 여럿 있어도 좌표를 판정한다")
    void judgesWhenManyBoundariesAreLarge() {
        // given
        long areaId = 0;
        for (int i = 0; i < 10; i++) {
            long id = protectedAreaFixture.insert(
                    aProtectedArea().longLatBoundary(circleWkt(126.0 + i * 0.5, 36.0, 0.05, 30_000)));
            if (i == 6) {
                areaId = id;
            }
        }
        GeoPoint center = new GeoPoint(36.0, 129.0);
        GeoPoint outside = new GeoPoint(36.0, 128.75);
        long bakjiId = insertSpot("BAKJI", "ACTIVE", center);
        clock.setInstant(JUDGED_AT);

        // when
        ParkAreaJudgement inside = parkAreaJudgeService.judge(center);
        ParkAreaJudgement away = parkAreaJudgeService.judge(outside);
        rejudgeAll();

        // then
        assertThat(inside.parkWarning()).isTrue();
        assertThat(inside.protectedAreaId()).isEqualTo(areaId);
        assertThat(away.parkWarning()).isFalse();
        assertThat(away.protectedAreaId()).isNull();
        assertThat(judgementOf(bakjiId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
    }

    @Test
    @DisplayName("[F-06][F-07] 재판정은 상태와 상관없이 모든 박지를 새 경계로 다시 판정하고, 박지가 아닌 장소와 updated_at은 건드리지 않는다")
    void rejudgesEveryBakjiWithCurrentBoundaries() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea().longLatBoundary(PARK_SQUARE));
        long staleAreaId = protectedAreaFixture.insert(
                aProtectedArea().longLatBoundary("MULTIPOLYGON(((129 35, 129.5 35, 129.5 35.5, 129 35.5, 129 35)))"));
        long insideId = insertSpot("BAKJI", "ACTIVE", INSIDE_PARK);
        long outsideId = insertSpot("BAKJI", "ACTIVE", OUTSIDE_EVERY_AREA);
        long staleWarnedId = insertSpot("BAKJI", "ACTIVE", OUTSIDE_EVERY_AREA);
        jdbcTemplate.update(
                "UPDATE spot SET park_warning = TRUE, protected_area_id = ?, area_checked_at = ? WHERE id = ?",
                staleAreaId,
                Timestamp.from(SAVED_AT),
                staleWarnedId);
        long hiddenId = insertSpot("BAKJI", "HIDDEN", INSIDE_PARK);
        long deletedId = insertSpot("BAKJI", "DELETED", INSIDE_PARK);
        long pendingId = insertSpot("BAKJI", "PENDING_REVIEW", INSIDE_PARK);
        long campsiteId = insertSpot("CAMPSITE", "ACTIVE", INSIDE_PARK);
        clock.setInstant(JUDGED_AT);

        // when
        BakjiRejudgeCounts counts = rejudgeAll();

        // then
        assertThat(counts).isEqualTo(new BakjiRejudgeCounts(6, 4, 1));
        assertThat(judgementOf(insideId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(outsideId)).isEqualTo(new SpotJudgementRow(false, null, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(staleWarnedId)).isEqualTo(new SpotJudgementRow(false, null, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(hiddenId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(deletedId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(pendingId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(campsiteId)).isEqualTo(new SpotJudgementRow(false, null, null, SAVED_AT));
    }

    @Test
    @DisplayName("[F-06][F-07] 경고를 유지하면서 근거 경계만 바뀐 박지는 새 경고와 해제에 세지 않고 새 경계로 기록한다")
    void doesNotCountBakjiWhoseWarningStaysWithChangedArea() {
        // given
        long oldAreaId = protectedAreaFixture.insert(
                aProtectedArea().longLatBoundary("MULTIPOLYGON(((129 35, 129.5 35, 129.5 35.5, 129 35.5, 129 35)))"));
        long newAreaId = protectedAreaFixture.insert(aProtectedArea().longLatBoundary(PARK_SQUARE));
        long bakjiId = insertSpot("BAKJI", "ACTIVE", INSIDE_PARK);
        jdbcTemplate.update(
                "UPDATE spot SET park_warning = TRUE, protected_area_id = ?, area_checked_at = ? WHERE id = ?",
                oldAreaId,
                Timestamp.from(SAVED_AT),
                bakjiId);
        clock.setInstant(JUDGED_AT);

        // when
        BakjiRejudgeCounts counts = rejudgeAll();

        // then
        assertThat(counts).isEqualTo(new BakjiRejudgeCounts(1, 0, 0));
        assertThat(judgementOf(bakjiId)).isEqualTo(new SpotJudgementRow(true, newAreaId, JUDGED_AT, SAVED_AT));
    }

    @Test
    @DisplayName("[F-06][F-07] 재판정은 한 번에 기록하는 묶음(1,000건)보다 박지가 많아도 모든 박지를 판정한다")
    void rejudgesEveryBakjiAcrossUpdateChunks() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea().longLatBoundary(PARK_SQUARE));
        String prefix = TestSequence.unique("대량박지");
        int insideCount = 600;
        int outsideCount = 401;
        insertBakjiBatch(prefix + "-in", INSIDE_PARK, insideCount);
        insertBakjiBatch(prefix + "-out", OUTSIDE_EVERY_AREA, outsideCount);
        clock.setInstant(JUDGED_AT);

        // when
        BakjiRejudgeCounts counts = rejudgeAll();

        // then
        assertThat(counts.judged()).isGreaterThanOrEqualTo(insideCount + outsideCount);
        assertThat(countBatchSpots(prefix + "-in%", true, areaId)).isEqualTo(insideCount);
        assertThat(countBatchSpots(prefix + "-out%", false, null)).isEqualTo(outsideCount);
    }

    @Test
    @DisplayName("[F-06][F-07] 재판정 묶음은 afterId보다 큰 박지만 ID 순서로 limit건 판정하고, 남은 박지가 없으면 빈 마지막 ID를 돌려준다")
    void rejudgesOnlyOneChunkAfterGivenId() {
        // given
        long areaId = protectedAreaFixture.insert(aProtectedArea().longLatBoundary(PARK_SQUARE));
        long firstId = insertSpot("BAKJI", "ACTIVE", INSIDE_PARK);
        long secondId = insertSpot("BAKJI", "ACTIVE", INSIDE_PARK);
        long thirdId = insertSpot("BAKJI", "ACTIVE", INSIDE_PARK);
        clock.setInstant(JUDGED_AT);

        // when
        BakjiRejudgeChunk chunk = parkAreaJudgeService.rejudgeBakjiChunk(firstId, 1, JUDGED_AT);

        // then
        assertThat(chunk).isEqualTo(new BakjiRejudgeChunk(new BakjiRejudgeCounts(1, 1, 0), secondId));
        assertThat(judgementOf(firstId)).isEqualTo(new SpotJudgementRow(false, null, null, SAVED_AT));
        assertThat(judgementOf(secondId)).isEqualTo(new SpotJudgementRow(true, areaId, JUDGED_AT, SAVED_AT));
        assertThat(judgementOf(thirdId)).isEqualTo(new SpotJudgementRow(false, null, null, SAVED_AT));
        assertThat(parkAreaJudgeService.rejudgeBakjiChunk(thirdId, 1, JUDGED_AT))
                .isEqualTo(new BakjiRejudgeChunk(new BakjiRejudgeCounts(0, 0, 0), null));
    }

    private BakjiRejudgeCounts rejudgeAll() {
        Instant now = clock.instant();
        BakjiRejudgeCounts total = new BakjiRejudgeCounts(0, 0, 0);
        long afterId = 0;
        while (true) {
            BakjiRejudgeChunk chunk = parkAreaJudgeService.rejudgeBakjiChunk(afterId, 1_000, now);
            if (chunk.lastId() == null) {
                return total;
            }
            total = total.plus(chunk.counts());
            afterId = chunk.lastId();
        }
    }

    private void insertBakjiBatch(String namePrefix, GeoPoint at, int count) {
        String wkt = String.format(Locale.ROOT, "POINT(%s %s)", at.latitude(), at.longitude());
        jdbcTemplate.batchUpdate(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, namePrefix + "-" + i);
                        ps.setString(2, wkt);
                        ps.setTimestamp(3, Timestamp.from(SAVED_AT));
                        ps.setTimestamp(4, Timestamp.from(SAVED_AT));
                    }

                    @Override
                    public int getBatchSize() {
                        return count;
                    }
                });
    }

    private int countBatchSpots(String namePattern, boolean expectedWarning, Long expectedAreaId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spot WHERE name LIKE ? AND park_warning = ?"
                        + " AND protected_area_id <=> ? AND area_checked_at = ?",
                Integer.class,
                namePattern,
                expectedWarning,
                expectedAreaId,
                Timestamp.from(JUDGED_AT));
    }

    private String circleWkt(double centerLng, double centerLat, double radius, int vertexCount) {
        StringBuilder wkt = new StringBuilder("MULTIPOLYGON(((");
        for (int i = 0; i < vertexCount; i++) {
            double angle = 2 * Math.PI * i / vertexCount;
            wkt.append(String.format(
                    Locale.ROOT,
                    "%.7f %.7f, ",
                    centerLng + radius * Math.cos(angle),
                    centerLat + radius * Math.sin(angle)));
        }
        wkt.append(String.format(Locale.ROOT, "%.7f %.7f)))", centerLng + radius, centerLat));
        return wkt.toString();
    }

    private long insertSpot(String type, String status, GeoPoint at) {
        return spotFixture.insert(aSpot().type(type)
                .status(status)
                .at(at.latitude(), at.longitude())
                .createdAt(SAVED_AT));
    }

    private SpotJudgementRow judgementOf(long spotId) {
        return jdbcTemplate.queryForObject(
                "SELECT park_warning, protected_area_id, area_checked_at, updated_at FROM spot WHERE id = ?",
                (rs, rowNum) -> {
                    Timestamp checkedAt = rs.getTimestamp("area_checked_at");
                    return new SpotJudgementRow(
                            rs.getBoolean("park_warning"),
                            rs.getObject("protected_area_id", Long.class),
                            checkedAt == null ? null : checkedAt.toInstant(),
                            rs.getTimestamp("updated_at").toInstant());
                },
                spotId);
    }

    private record SpotJudgementRow(
            boolean parkWarning, Long protectedAreaId, Instant areaCheckedAt, Instant updatedAt) {}
}
