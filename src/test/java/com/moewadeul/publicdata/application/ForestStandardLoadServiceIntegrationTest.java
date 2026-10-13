package com.moewadeul.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.spot.application.PublicSpotCommand;
import com.moewadeul.spot.application.PublicSpotSource;
import com.moewadeul.spot.application.PublicSpotSyncService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

// 픽스처(fixtures/publicdata/forest-standard/)는 손으로 지어낸 휴양림 5행이다.
// 가나 자연휴양림과 가나자연휴양림(시립)은 약 7m 떨어진 중복 행이라 한 휴양림이 되고, 기준일이 늦은 시립 행이 대표다.
// 다라(36.5, 128.5), 마바(35.5, 127.0)는 따로 있는 휴양림이고, 사아 휴양림은 숙박가능여부가 N이다.
@IntegrationTest
class ForestStandardLoadServiceIntegrationTest {

    private static final String DIR = "fixtures/publicdata/forest-standard/";
    private static final String BASE = DIR + "forest-standard.json";
    private static final String WITHOUT_DARA = DIR + "forest-standard-without-b.json";
    private static final String RENAMED_DARA = DIR + "forest-standard-renamed.json";
    private static final String NO_LODGING = DIR + "forest-standard-no-lodging.json";

    private static final int ROWS = 5;
    private static final int FOREST_COUNT = 3;
    private static final String GANA = "가나자연휴양림(시립)";
    private static final String DARA = "다라 자연휴양림";
    private static final String MABA = "마바 휴양림";

    @Autowired
    private ForestStandardLoadService loadService;

    @Autowired
    private PublicSpotSyncService publicSpotSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-06] 표준데이터 파일을 처음 적재하면 중복 행은 한 휴양림으로 묶고 숙박 불가 행은 빼서 FOREST 장소로 저장한다")
    void firstLoadGroupsDuplicatesAndSkipsNoLodging() throws IOException {
        // when
        ForestStandardLoadResult result = loadService.load(fixture(BASE)).orElseThrow();

        // then
        assertThat(result)
                .isEqualTo(new ForestStandardLoadResult(
                        LocalDate.of(2026, 9, 10), ROWS, FOREST_COUNT, FOREST_COUNT, 0, 0, 0, 1, 0, 0, 0));
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT type FROM spot", String.class))
                .containsExactly("FOREST");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT source FROM public_spot_detail", String.class))
                .containsExactly("FOREST_STANDARD");
        assertThat(jdbcTemplate.queryForList("SELECT s.name FROM spot s ORDER BY s.name", String.class))
                .containsExactlyInAnyOrder(GANA, DARA, MABA);
        assertThat(jdbcTemplate.queryForList("SELECT external_id FROM public_spot_detail", String.class))
                .allSatisfy(id -> assertThat(id).matches("RF-[0-9a-f]{24}"));
        Map<String, Object> gana = jdbcTemplate.queryForMap(
                "SELECT s.address, d.phone, d.homepage, CAST(d.source_date AS CHAR) AS source_date FROM spot s"
                        + " JOIN public_spot_detail d ON d.spot_id = s.id WHERE s.name = ?",
                GANA);
        assertThat(gana)
                .containsEntry("address", "경기도 가나시 숲길 1-1")
                .containsEntry("phone", "031-111-2222")
                .containsEntry("homepage", "https://example.com/a2")
                .containsEntry("source_date", "2026-09-10");
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("COMPLETED");
            assertThat(run.processedCount()).isEqualTo(ROWS);
            assertThat(run.skippedCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("[F-06] 연락처가 비어 있는 휴양림은 전화번호와 홈페이지를 null로 저장한다")
    void storesBlankPhoneAndHomepageAsNull() throws IOException {
        // when
        loadService.load(fixture(BASE));

        // then
        Map<String, Object> maba = jdbcTemplate.queryForMap(
                "SELECT d.phone, d.homepage FROM public_spot_detail d JOIN spot s ON s.id = d.spot_id"
                        + " WHERE s.name = ?",
                MABA);
        assertThat(maba.get("phone")).isNull();
        assertThat(maba.get("homepage")).isNull();
    }

    @Test
    @DisplayName("[F-06] 같은 파일을 다시 적재하면 모두 바뀌지 않은 것으로 세고 장소 값과 시각이 그대로다")
    void reloadingSameFileLeavesSpotsUnchanged() throws IOException {
        // given
        loadService.load(fixture(BASE));
        List<Map<String, Object>> before = spotRows();
        clock.advance(Duration.ofDays(1));

        // when
        ForestStandardLoadResult result = loadService.load(fixture(BASE)).orElseThrow();

        // then
        assertThat(result.inserted()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isEqualTo(FOREST_COUNT);
        assertThat(result.removed()).isZero();
        assertThat(spotRows()).isEqualTo(before);
    }

    @Test
    @DisplayName("[F-06] 파일에서 빠진 휴양림은 숨기고, 파일에 다시 나타나면 되돌린다")
    void hidesMissingForestAndRestoresWhenItReturns() throws IOException {
        // given
        loadService.load(fixture(BASE));

        // when
        ForestStandardLoadResult removedLoad =
                loadService.load(fixture(WITHOUT_DARA)).orElseThrow();

        // then
        assertThat(removedLoad.removed()).isEqualTo(1);
        assertThat(statusOf(DARA)).isEqualTo("HIDDEN");
        assertThat(removedMarkerCount(DARA)).isEqualTo(1);
        assertThat(statusOf(GANA)).isEqualTo("ACTIVE");
        assertThat(statusOf(MABA)).isEqualTo("ACTIVE");

        // when
        clock.advance(Duration.ofDays(1));
        ForestStandardLoadResult restoredLoad = loadService.load(fixture(BASE)).orElseThrow();

        // then
        assertThat(restoredLoad.removed()).isZero();
        assertThat(statusOf(DARA)).isEqualTo("ACTIVE");
        assertThat(removedMarkerCount(DARA)).isZero();
        assertThat(countRows("spot")).isEqualTo(FOREST_COUNT);
    }

    @Test
    @DisplayName("[F-06] 국립 휴양림과 겹치는 휴양림은 건너뛰고, 이름이 같고 1.5km 안인 휴양림도 건너뛴다")
    void skipsForestsOverlappingNational() throws IOException {
        // given: 다라의 33m 옆에 이름이 다른 국립 장소를, 마바의 1km 옆에 이름 핵심이 겹치는 국립 장소를 둔다.
        insertNational("N1", "전혀 다른 국립 자연휴양림", 36.5003, 128.5);
        insertNational("N2", "국립 마바산 자연휴양림", 35.509, 127.0);

        // when
        ForestStandardLoadResult result = loadService.load(fixture(BASE)).orElseThrow();

        // then
        assertThat(result.groupCount()).isEqualTo(FOREST_COUNT);
        assertThat(result.skippedNational()).isEqualTo(2);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT s.name FROM spot s JOIN public_spot_detail d ON d.spot_id = s.id"
                                + " WHERE d.source = 'FOREST_STANDARD'",
                        String.class))
                .containsExactly(GANA);
        assertThat(runs())
                .singleElement()
                .satisfies(run -> assertThat(run.skippedCount()).isEqualTo(3));
    }

    @Test
    @DisplayName("[F-06] 이미 적재한 휴양림이 나중에 국립 파일과 겹치게 되면 다음 적재에서 숨긴다")
    void hidesForestThatLaterOverlapsNational() throws IOException {
        // given
        loadService.load(fixture(BASE));
        insertNational("N1", "전혀 다른 국립 자연휴양림", 36.5003, 128.5);
        clock.advance(Duration.ofDays(1));

        // when
        ForestStandardLoadResult result = loadService.load(fixture(BASE)).orElseThrow();

        // then
        assertThat(result.skippedNational()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(statusOf(DARA)).isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("[F-06] 이름을 바꾼 파일을 적재하면 같은 장소의 이름을 갱신하고 장소 수는 그대로다")
    void renamedForestKeepsSameSpot() throws IOException {
        // given
        loadService.load(fixture(BASE));
        String externalIdBefore = externalIdOf(DARA);
        clock.advance(Duration.ofDays(1));

        // when
        ForestStandardLoadResult result =
                loadService.load(fixture(RENAMED_DARA)).orElseThrow();

        // then
        assertThat(result.inserted()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(FOREST_COUNT - 1);
        assertThat(result.removed()).isZero();
        assertThat(countRows("spot")).isEqualTo(FOREST_COUNT);
        assertThat(externalIdOf("다라숲 자연휴양림")).isEqualTo(externalIdBefore);
        assertThat(countRows("spot WHERE name = '" + DARA + "'")).isZero();
    }

    @Test
    @DisplayName("[F-06] 관리자가 숨긴 휴양림은 파일에서 빠져도 표시하지 않고, 다시 나타나도 공개하지 않는다")
    void leavesAdminHiddenForestUntouched() throws IOException {
        // given
        loadService.load(fixture(BASE));
        jdbcTemplate.update("UPDATE spot SET status = 'HIDDEN' WHERE name = ?", DARA);

        // when
        ForestStandardLoadResult removedLoad =
                loadService.load(fixture(WITHOUT_DARA)).orElseThrow();

        // then
        assertThat(removedLoad.removed()).isZero();
        assertThat(removedMarkerCount(DARA)).isZero();

        // when
        loadService.load(fixture(BASE));

        // then
        assertThat(statusOf(DARA)).isEqualTo("HIDDEN");
        assertThat(removedMarkerCount(DARA)).isZero();
        assertThat(countRows("spot")).isEqualTo(FOREST_COUNT);
    }

    @Test
    @DisplayName("[F-06] 적재할 휴양림이 하나도 없는 파일은 실행을 실패로 남기고 기존 휴양림을 숨기지 않는다")
    void failsRunAndHidesNothingWhenNoForestIsLoadable() throws IOException {
        // given
        loadService.load(fixture(BASE));

        // when, then
        assertThatThrownBy(() -> loadService.load(fixture(NO_LODGING)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("skippedNoLodging=4");
        assertThat(countRows("spot WHERE status = 'HIDDEN'")).isZero();
        assertThat(runs()).hasSize(2).last().satisfies(run -> {
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.errorMessage()).startsWith("IllegalStateException: ");
        });
    }

    @Test
    @DisplayName("[F-06] 파일 경로가 설정되지 않았으면 예외를 다시 던지고 설정 키를 실패 기록에 남긴다")
    void missingFilePathFailsRunWithPropertyName() {
        // when, then
        assertThatThrownBy(() -> loadService.load(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ForestStandardLoadProperties.FILE_PROPERTY);
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("FAILED");
            assertThat(run.errorMessage()).contains(ForestStandardLoadProperties.FILE_PROPERTY);
        });
    }

    @Test
    @DisplayName("[F-06] 인자 없이 호출하면 설정한 경로의 파일을 적재한다")
    void loadUsesConfiguredFile() {
        // when
        ForestStandardLoadResult result = loadService.load().orElseThrow();

        // then
        assertThat(result.inserted()).isEqualTo(FOREST_COUNT);
    }

    @Test
    @DisplayName("[F-06] 표준데이터를 적재해도 국립 휴양림은 숨기지 않는다")
    void doesNotHideNationalForest() throws IOException {
        // given: 파일의 어느 휴양림과도 겹치지 않는 국립 장소 둘이다.
        insertNational("N1", "전혀 다른 국립 자연휴양림", 37.0, 128.0);
        insertNational("N2", "또 다른 국립 자연휴양림", 35.0, 129.0);

        // when
        ForestStandardLoadResult result = loadService.load(fixture(BASE)).orElseThrow();

        // then
        assertThat(result.removed()).isZero();
        assertThat(countRows("spot s JOIN public_spot_detail d ON d.spot_id = s.id"
                        + " WHERE d.source = 'FOREST' AND s.status = 'ACTIVE'"))
                .isEqualTo(2);
    }

    private void insertNational(String externalId, String name, double lat, double lng) {
        publicSpotSyncService.upsert(
                PublicSpotSource.FOREST,
                List.of(new PublicSpotCommand(
                        externalId, name, lat, lng, null, null, null, null, null, null, null, null, null, Set.of())));
    }

    private String statusOf(String name) {
        return jdbcTemplate.queryForObject("SELECT status FROM spot WHERE name = ?", String.class, name);
    }

    private String externalIdOf(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT d.external_id FROM public_spot_detail d JOIN spot s ON s.id = d.spot_id WHERE s.name = ?",
                String.class,
                name);
    }

    private int removedMarkerCount(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_spot_detail d JOIN spot s ON s.id = d.spot_id"
                        + " WHERE s.name = ? AND d.source_removed_at IS NOT NULL",
                Integer.class,
                name);
    }

    private static Path fixture(String path) throws IOException {
        return new ClassPathResource(path).getFile().toPath();
    }

    private int countRows(String tableAndCondition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private List<Map<String, Object>> spotRows() {
        return jdbcTemplate.queryForList("SELECT id, type, name, ST_AsText(location) AS location, address, status,"
                + " created_at, updated_at FROM spot ORDER BY id");
    }

    private List<RunRow> runs() {
        return jdbcTemplate.query(
                "SELECT status, processed_count, skipped_count, error_message, finished_at"
                        + " FROM sync_job_run WHERE job_type = 'FOREST_STANDARD' ORDER BY id",
                (rs, rowNum) -> new RunRow(
                        rs.getString("status"),
                        rs.getInt("processed_count"),
                        rs.getInt("skipped_count"),
                        rs.getString("error_message"),
                        rs.getObject("finished_at") != null));
    }

    private record RunRow(String status, int processedCount, int skippedCount, String errorMessage, boolean finished) {}
}
