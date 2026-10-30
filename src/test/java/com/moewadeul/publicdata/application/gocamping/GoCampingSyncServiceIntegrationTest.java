package com.moewadeul.publicdata.application.gocamping;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.publicdata.infra.gocamping.GoCampingApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@IntegrationTest
class GoCampingSyncServiceIntegrationTest {

    private static final String BASED_SYNC_LIST_PATH = "/B551011/GoCamping/basedSyncList";
    private static final String TEST_SERVICE_KEY = "test+key/value==";
    private static final int PAGE_COUNT = 4;
    private static final int RECEIVED_COUNT = 7;
    private static final int LIVE_COUNT = 6;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private GoCampingSyncService goCampingSyncService;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
    }

    @Test
    @DisplayName("[F-06][NFR-06] 동기화는 모든 페이지를 한 번씩 받아 장소와 상세를 적재하고 실행 기록을 완료로 남긴다")
    void syncLoadsAllPagesAndCompletesRun() {
        // given
        stubAllPages();

        // when
        Optional<GoCampingSyncResult> result = goCampingSyncService.sync();

        // then
        assertThat(result).isPresent();
        assertThat(result.get().pages()).isEqualTo(PAGE_COUNT);
        assertThat(result.get().processedCount()).isEqualTo(RECEIVED_COUNT);
        assertThat(result.get().inserted()).isEqualTo(LIVE_COUNT);
        assertThat(countRows("spot")).isEqualTo(LIVE_COUNT);
        assertThat(countRows("public_spot_detail")).isEqualTo(LIVE_COUNT);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT external_id FROM public_spot_detail WHERE source = 'GOCAMPING' ORDER BY external_id",
                        String.class))
                .containsExactly("146", "1467", "2329", "2931", "7001", "7002");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT type FROM spot", String.class))
                .containsExactly("CAMPSITE");
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("COMPLETED");
            assertThat(run.progressCursor()).isEqualTo(String.valueOf(PAGE_COUNT));
            assertThat(run.processedCount()).isEqualTo(RECEIVED_COUNT);
            assertThat(run.finished()).isTrue();
        });
        for (int pageNo = 1; pageNo <= PAGE_COUNT; pageNo++) {
            assertThat(requestCount(pageNo)).isEqualTo(1);
        }
        assertThat(requestCount(PAGE_COUNT + 1)).isZero();
    }

    @Test
    @DisplayName("[F-06] 운영 상태와 휴장 기간은 상세에 저장하고, 삭제 항목은 DB에 없으면 적재하지 않으며 받은 항목 수에는 센다")
    void syncStoresOperatingStatusAndClosedPeriodAndIgnoresRemovedItemNotInDb() {
        // given
        stubAllPages();

        // when
        GoCampingSyncResult result = goCampingSyncService.sync().orElseThrow();

        // then
        Map<String, Object> closedPeriod =
                jdbcTemplate.queryForMap("SELECT operating_status, CAST(closed_from AS CHAR) AS closed_from,"
                        + " CAST(closed_until AS CHAR) AS closed_until FROM public_spot_detail"
                        + " WHERE external_id = '1467'");
        assertThat(closedPeriod.get("operating_status")).isEqualTo("OPERATING");
        assertThat(closedPeriod.get("closed_from")).isEqualTo("2026-11-16");
        assertThat(closedPeriod.get("closed_until")).isEqualTo("2027-03-15");
        Map<String, Object> noPeriod =
                jdbcTemplate.queryForMap("SELECT operating_status, closed_from, closed_until FROM public_spot_detail"
                        + " WHERE external_id = '146'");
        assertThat(noPeriod.get("operating_status")).isEqualTo("OPERATING");
        assertThat(noPeriod.get("closed_from")).isNull();
        assertThat(noPeriod.get("closed_until")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM public_spot_detail WHERE external_id = '3466'", Integer.class))
                .isZero();
        assertThat(countRows("public_spot_detail WHERE source_removed_at IS NOT NULL"))
                .isZero();
        assertThat(result.removed()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.processedCount()).isEqualTo(RECEIVED_COUNT);
        assertThat(runs().getFirst().processedCount()).isEqualTo(RECEIVED_COUNT);
        assertThat(runs().getFirst().skippedCount()).isZero();
    }

    @Test
    @DisplayName("[F-06] 응답의 mapY를 위도, mapX를 경도로 저장한다")
    void syncStoresMapYAsLatitudeAndMapXAsLongitude() {
        // given
        stubAllPages();

        // when
        goCampingSyncService.sync();

        // then
        Map<String, Object> location = jdbcTemplate.queryForMap(
                "SELECT ST_Latitude(s.location) AS latitude, ST_Longitude(s.location) AS longitude FROM spot s"
                        + " JOIN public_spot_detail d ON d.spot_id = s.id WHERE d.external_id = '146'");
        assertThat(((Number) location.get("latitude")).doubleValue()).isCloseTo(35.4952105728394, within(1e-9));
        assertThat(((Number) location.get("longitude")).doubleValue()).isCloseTo(127.190085215523, within(1e-9));
    }

    @Test
    @DisplayName("[F-06] 좌표가 비어 있는 항목은 건너뛰고, 건너뛴 건수를 결과와 실행 기록에 남기며 받은 항목 수에는 센다")
    void syncSkipsItemWithoutCoordinateAndRecordsSkippedCount() {
        // given
        stubAllPages(Map.of("7001", Map.of("mapX", "", "mapY", "")));

        // when
        GoCampingSyncResult result = goCampingSyncService.sync().orElseThrow();

        // then
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(LIVE_COUNT - 1);
        assertThat(result.removed()).isZero();
        assertThat(result.processedCount()).isEqualTo(RECEIVED_COUNT);
        assertThat(countRows("spot")).isEqualTo(LIVE_COUNT - 1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM public_spot_detail WHERE external_id = '7001'", Integer.class))
                .isZero();
        assertThat(runs()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("COMPLETED");
            assertThat(run.skippedCount()).isEqualTo(1);
            assertThat(run.processedCount()).isEqualTo(RECEIVED_COUNT);
        });
    }

    @Test
    @DisplayName("[F-06][NFR-06] 중간 페이지에서 실패하면 실행 기록을 실패로 남기고, 다음 실행은 받은 페이지를 다시 부르지 않고 이어서 받는다")
    void syncResumesFromNextPageAfterFailure() {
        // given
        stubPage(1);
        stubPage(3);
        stubPage(4);
        wireMock.stubFor(get(urlPathEqualTo(BASED_SYNC_LIST_PATH))
                .withQueryParam("pageNo", equalTo("2"))
                .willReturn(aResponse().withStatus(500)));

        // when
        assertThatThrownBy(() -> goCampingSyncService.sync()).isInstanceOf(GoCampingApiException.class);

        // then
        RunRow failedRun = runs().getFirst();
        assertThat(failedRun.status()).isEqualTo("FAILED");
        assertThat(failedRun.progressCursor()).isEqualTo("1");
        assertThat(failedRun.processedCount()).isEqualTo(2);
        assertThat(failedRun.errorMessage())
                .isNotBlank()
                .doesNotContain(TEST_SERVICE_KEY)
                .doesNotContain("test%2Bkey")
                .doesNotContain("serviceKey");
        assertThat(countRows("spot")).isEqualTo(2);

        // when
        stubPage(2);
        clock.advance(Duration.ofHours(1));
        goCampingSyncService.sync();

        // then
        List<RunRow> runs = runs();
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).status()).isEqualTo("FAILED");
        assertThat(runs.get(1).status()).isEqualTo("COMPLETED");
        assertThat(runs.get(1).progressCursor()).isEqualTo(String.valueOf(PAGE_COUNT));
        assertThat(runs.get(1).processedCount()).isEqualTo(5);
        assertThat(requestCount(1)).isEqualTo(1);
        assertThat(requestCount(2)).isEqualTo(3);
        assertThat(requestCount(3)).isEqualTo(1);
        assertThat(requestCount(4)).isEqualTo(1);
        assertThat(countRows("spot")).isEqualTo(LIVE_COUNT);
        assertThat(countRows("public_spot_detail")).isEqualTo(LIVE_COUNT);
    }

    @Test
    @DisplayName("[F-06][NFR-06] 이어받은 실행이 한 페이지도 끝내지 못하고 실패하면 진행 기록이 없으므로, 다음 실행은 처음 페이지부터 다시 받는다")
    void syncRestartsFromFirstPageWhenResumedRunFailedWithoutProgress() {
        // given
        stubPage(1);
        stubPage(3);
        stubPage(4);
        wireMock.stubFor(get(urlPathEqualTo(BASED_SYNC_LIST_PATH))
                .withQueryParam("pageNo", equalTo("2"))
                .willReturn(aResponse().withStatus(500)));

        // when
        assertThatThrownBy(() -> goCampingSyncService.sync()).isInstanceOf(GoCampingApiException.class);
        clock.advance(Duration.ofHours(1));
        assertThatThrownBy(() -> goCampingSyncService.sync()).isInstanceOf(GoCampingApiException.class);
        clock.advance(Duration.ofHours(1));
        assertThatThrownBy(() -> goCampingSyncService.sync()).isInstanceOf(GoCampingApiException.class);

        // then
        List<RunRow> runs = runs();
        assertThat(runs).hasSize(3).allSatisfy(run -> assertThat(run.status()).isEqualTo("FAILED"));
        assertThat(runs.get(0).processedCount()).isEqualTo(2);
        assertThat(runs.get(1).progressCursor()).isEqualTo("1");
        assertThat(runs.get(1).processedCount()).isZero();
        assertThat(requestCount(1)).isEqualTo(2);
        assertThat(runs.get(2).progressCursor()).isEqualTo("1");
        assertThat(runs.get(2).processedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-06] 원천 수정일(modifiedtime)을 상세의 기준일로 저장하고, 수정일만 바뀐 항목은 그 행만 갱신한다")
    void syncStoresModifiedTimeAsSourceDateAndUpdatesOnlyChangedRow() {
        // given
        stubAllPages();
        goCampingSyncService.sync();
        assertThat(sourceDate("146")).isEqualTo("2026-10-02");
        assertThat(sourceDate("2931")).isEqualTo("2026-10-02");
        Map<String, Object> changedBefore = detailRow("146");
        Map<String, Object> othersBefore = detailRow("2931");

        // when
        clock.advance(Duration.ofDays(1));
        stubAllPages(Map.of("146", Map.of("modifiedtime", "2026-10-09")));
        Optional<GoCampingSyncResult> second = goCampingSyncService.sync();

        // then
        assertThat(second).isPresent();
        assertThat(second.get().updated()).isEqualTo(1);
        assertThat(second.get().unchanged()).isEqualTo(LIVE_COUNT - 1);
        Map<String, Object> changedAfter = detailRow("146");
        assertThat(changedAfter.get("source_date")).isEqualTo("2026-10-09");
        assertThat(changedAfter.get("synced_at")).isNotEqualTo(changedBefore.get("synced_at"));
        assertThat(detailRow("2931")).isEqualTo(othersBefore);
    }

    @Test
    @DisplayName("[F-06] 같은 데이터를 두 번 적재해도 행이 늘지 않고, 바뀐 것이 없으면 수정 시각도 그대로다")
    void syncingSameDataTwiceKeepsRowsUnchanged() {
        // given
        stubAllPages();
        goCampingSyncService.sync();
        List<Map<String, Object>> spotsAfterFirst = spotRows();
        List<Map<String, Object>> detailsAfterFirst = detailRows();

        // when
        clock.advance(Duration.ofDays(1));
        Optional<GoCampingSyncResult> second = goCampingSyncService.sync();

        // then
        assertThat(second).isPresent();
        assertThat(second.get().inserted()).isZero();
        assertThat(second.get().updated()).isZero();
        assertThat(second.get().unchanged()).isEqualTo(LIVE_COUNT);
        assertThat(second.get().removed()).isZero();
        assertThat(second.get().skipped()).isZero();
        assertThat(spotRows()).isEqualTo(spotsAfterFirst);
        assertThat(detailRows()).isEqualTo(detailsAfterFirst);
        assertThat(runs()).hasSize(2).allSatisfy(run -> assertThat(run.status()).isEqualTo("COMPLETED"));
    }

    @Test
    @DisplayName("[F-06] 원천에서 삭제된 항목은 장소를 숨기고, 같은 삭제 항목이 다시 와도 그대로 두며, 살아 있는 항목으로 돌아오면 다시 공개한다")
    void syncHidesRemovedSpotAndRestoresItWhenItComesBack() {
        // given
        stubAllPages();
        goCampingSyncService.sync();
        assertThat(spotStatus("2931")).isEqualTo("ACTIVE");
        assertThat(sourceRemovedAt("2931")).isNull();

        // when
        stubAllPages(Map.of("2931", Map.of("syncStatus", "D")));
        clock.advance(Duration.ofDays(1));
        GoCampingSyncResult hidden = goCampingSyncService.sync().orElseThrow();

        // then
        assertThat(hidden.removed()).isEqualTo(1);
        assertThat(hidden.skipped()).isZero();
        assertThat(hidden.inserted()).isZero();
        assertThat(hidden.updated()).isZero();
        assertThat(hidden.unchanged()).isEqualTo(LIVE_COUNT - 1);
        assertThat(hidden.processedCount()).isEqualTo(RECEIVED_COUNT);
        assertThat(spotStatus("2931")).isEqualTo("HIDDEN");
        String removedAt = sourceRemovedAt("2931");
        assertThat(removedAt).isNotNull();
        assertThat(countRows("spot WHERE status = 'HIDDEN'")).isEqualTo(1);
        assertThat(runs().getLast().skippedCount()).isZero();

        // when
        clock.advance(Duration.ofDays(1));
        GoCampingSyncResult again = goCampingSyncService.sync().orElseThrow();

        // then
        assertThat(again.removed()).isZero();
        assertThat(again.inserted()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(again.unchanged()).isEqualTo(LIVE_COUNT - 1);
        assertThat(spotStatus("2931")).isEqualTo("HIDDEN");
        assertThat(sourceRemovedAt("2931")).isEqualTo(removedAt);

        // when
        stubAllPages(Map.of("2931", Map.of("syncStatus", "U")));
        clock.advance(Duration.ofDays(1));
        GoCampingSyncResult restored = goCampingSyncService.sync().orElseThrow();

        // then
        assertThat(restored.removed()).isZero();
        assertThat(restored.updated()).isEqualTo(1);
        assertThat(restored.inserted()).isZero();
        assertThat(restored.unchanged()).isEqualTo(LIVE_COUNT - 1);
        assertThat(spotStatus("2931")).isEqualTo("ACTIVE");
        assertThat(sourceRemovedAt("2931")).isNull();
        assertThat(countRows("spot WHERE status = 'HIDDEN'")).isZero();
    }

    @Test
    @DisplayName("[F-06] 관리자가 숨긴 장소가 삭제 항목으로 와도 건드리거나 표시하지 않고, 살아 있는 항목으로 돌아와도 숨긴 채로 둔다")
    void syncLeavesAdminHiddenSpotUntouchedWhenItArrivesAsRemoved() {
        // given
        stubAllPages();
        goCampingSyncService.sync();
        jdbcTemplate.update("UPDATE spot SET status = 'HIDDEN' WHERE id = (SELECT spot_id FROM public_spot_detail"
                + " WHERE external_id = '2931')");
        List<Map<String, Object>> spotsBefore = spotRows();

        // when
        stubAllPages(Map.of("2931", Map.of("syncStatus", "D")));
        clock.advance(Duration.ofDays(1));
        GoCampingSyncResult removedRun = goCampingSyncService.sync().orElseThrow();

        // then
        assertThat(removedRun.removed()).isZero();
        assertThat(spotStatus("2931")).isEqualTo("HIDDEN");
        assertThat(sourceRemovedAt("2931")).isNull();
        assertThat(spotRows()).isEqualTo(spotsBefore);

        // when
        stubAllPages();
        clock.advance(Duration.ofDays(1));
        goCampingSyncService.sync();

        // then
        assertThat(spotStatus("2931")).isEqualTo("HIDDEN");
        assertThat(sourceRemovedAt("2931")).isNull();
    }

    @Test
    @DisplayName("[F-06] 오래 갱신되지 않은 RUNNING 기록은 실패로 처리하고, 그 기록의 진행 위치 다음 페이지부터 이어서 받는다")
    void syncFailsStaleRunningRunAndResumesFromItsCursor() {
        // given
        stubAllPages();
        long staleRunId = insertRunningRun(clock.instant().minus(Duration.ofMinutes(31)));

        // when
        Optional<GoCampingSyncResult> result = goCampingSyncService.sync();

        // then
        assertThat(result).isPresent();
        List<RunRow> runs = runs();
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).id()).isEqualTo(staleRunId);
        assertThat(runs.get(0).status()).isEqualTo("FAILED");
        assertThat(runs.get(0).errorMessage()).isNotBlank();
        assertThat(runs.get(0).finished()).isTrue();
        assertThat(runs.get(1).status()).isEqualTo("COMPLETED");
        assertThat(runs.get(1).progressCursor()).isEqualTo(String.valueOf(PAGE_COUNT));
        assertThat(requestCount(1)).isZero();
        assertThat(requestCount(2)).isEqualTo(1);
        assertThat(countRows("spot")).isEqualTo(4);
    }

    @Test
    @DisplayName("[F-06][NFR-06] 최근에 갱신된 RUNNING 기록이 있으면 다른 실행이 진행 중이므로 API를 부르지 않고 건너뛴다")
    void syncSkipsWhenAnotherRunIsInProgress() {
        // given
        stubAllPages();
        insertRunningRun(clock.instant().minus(Duration.ofMinutes(5)));

        // when
        Optional<GoCampingSyncResult> result = goCampingSyncService.sync();

        // then
        assertThat(result).isEmpty();
        wireMock.verify(0, anyRequestedFor(anyUrl()));
        assertThat(runs())
                .singleElement()
                .satisfies(run -> assertThat(run.status()).isEqualTo("RUNNING"));
    }

    private void stubAllPages() {
        stubAllPages(Map.of());
    }

    private void stubAllPages(Map<String, Map<String, String>> itemOverrides) {
        for (int pageNo = 1; pageNo <= PAGE_COUNT; pageNo++) {
            stubPage(pageNo, itemOverrides);
        }
    }

    private void stubPage(int pageNo) {
        stubPage(pageNo, Map.of());
    }

    private void stubPage(int pageNo, Map<String, Map<String, String>> itemOverrides) {
        wireMock.stubFor(get(urlPathEqualTo(BASED_SYNC_LIST_PATH))
                .withQueryParam("pageNo", equalTo(String.valueOf(pageNo)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=UTF-8")
                        .withBody(readFixture(pageNo, itemOverrides))));
    }

    private static String readFixture(int pageNo, Map<String, Map<String, String>> itemOverrides) {
        String path = "fixtures/publicdata/gocamping/sync/based-sync-list-page" + pageNo + ".json";
        try {
            String json = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            if (itemOverrides.isEmpty()) {
                return json;
            }
            JsonNode root = JSON_MAPPER.readTree(json);
            for (JsonNode item :
                    root.path("response").path("body").path("items").path("item")) {
                Map<String, String> fields =
                        itemOverrides.get(item.path("contentId").asString());
                if (fields != null) {
                    fields.forEach((name, value) -> ((ObjectNode) item).put(name, value));
                }
            }
            return JSON_MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int requestCount(int pageNo) {
        return wireMock.countRequestsMatching(getRequestedFor(urlPathEqualTo(BASED_SYNC_LIST_PATH))
                        .withQueryParam("pageNo", equalTo(String.valueOf(pageNo)))
                        .build())
                .getCount();
    }

    private int countRows(String tableAndCondition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private String spotStatus(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT s.status FROM spot s JOIN public_spot_detail d ON d.spot_id = s.id WHERE d.external_id = ?",
                String.class,
                externalId);
    }

    private String sourceRemovedAt(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(source_removed_at AS CHAR) FROM public_spot_detail WHERE external_id = ?",
                String.class,
                externalId);
    }

    private List<Map<String, Object>> spotRows() {
        return jdbcTemplate.queryForList("SELECT id, type, name, ST_AsText(location) AS location, address, weather_nx,"
                + " weather_ny, park_warning, status, created_at, updated_at FROM spot ORDER BY id");
    }

    private String sourceDate(String externalId) {
        return (String) detailRow(externalId).get("source_date");
    }

    private Map<String, Object> detailRow(String externalId) {
        return detailRows().stream()
                .filter(row -> externalId.equals(row.get("external_id")))
                .findFirst()
                .orElseThrow();
    }

    private List<Map<String, Object>> detailRows() {
        return jdbcTemplate.queryForList("SELECT spot_id, source, external_id, category, CAST(facilities AS CHAR)"
                + " AS facilities, phone, homepage, operating_status, CAST(closed_from AS CHAR) AS closed_from,"
                + " CAST(closed_until AS CHAR) AS closed_until, CAST(source_date AS CHAR) AS source_date, source_removed_at, synced_at, created_at, updated_at"
                + " FROM public_spot_detail ORDER BY spot_id");
    }

    private long insertRunningRun(Instant updatedAt) {
        jdbcTemplate.update(
                "INSERT INTO sync_job_run (job_type, status, progress_cursor, processed_count, started_at,"
                        + " created_at, updated_at) VALUES ('GOCAMPING', 'RUNNING', '1', 2, ?, ?, ?)",
                updatedAt,
                updatedAt,
                updatedAt);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM sync_job_run", Long.class);
    }

    private List<RunRow> runs() {
        return jdbcTemplate.query(
                "SELECT id, status, progress_cursor, processed_count, skipped_count, error_message, finished_at"
                        + " FROM sync_job_run WHERE job_type = 'GOCAMPING' ORDER BY id",
                (rs, rowNum) -> new RunRow(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getString("progress_cursor"),
                        rs.getInt("processed_count"),
                        rs.getInt("skipped_count"),
                        rs.getString("error_message"),
                        rs.getObject("finished_at") != null));
    }

    private record RunRow(
            long id,
            String status,
            String progressCursor,
            int processedCount,
            int skippedCount,
            String errorMessage,
            boolean finished) {}
}
