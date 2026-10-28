package com.moewadeul.spot.application.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.domain.SpotType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotMapQueryServiceIntegrationTest {

    private static final Area SEORAK_AREA = new Area(37.5, 128.5, 38.0, 129.0);
    private static final Coordinate SEORAK = new Coordinate(37.71, 128.75);
    private static final Coordinate SEOUL_CITY_HALL = new Coordinate(37.5665, 126.978);
    private static final Coordinate TAEBAEK = new Coordinate(37.16, 128.98);

    private static final Area BULK_AREA = new Area(36.0, 127.0, 37.0, 128.0);

    private static final Function<Area, SpotAreaQuery> BULK_FILTER_QUERY = area -> new SpotAreaQuery(
            area.swLat(), area.swLng(), area.neLat(), area.neLng(), Set.of(), Set.of(), true, false, false, true);

    @Autowired
    private SpotMapQueryService spotMapQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-03] 영역 안의 장소만 찾고, 저장한 위도·경도를 그대로 돌려준다")
    void findsOnlySpotsInsideAreaWithSameLatitudeAndLongitude() {
        // given
        long seorakId = insertSpot("CAMPSITE", "설악 야영장", SEORAK);
        insertSpot("CAMPSITE", "서울시청 야영장", SEOUL_CITY_HALL);
        insertSpot("CAMPSITE", "태백 야영장", TAEBAEK);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(result.clusters()).isEmpty();
        assertThat(result.markers()).hasSize(1);
        SpotMarker marker = result.markers().getFirst();
        assertThat(marker.spotId()).isEqualTo(seorakId);
        assertThat(marker.type()).isEqualTo(SpotType.CAMPSITE);
        assertThat(marker.name()).isEqualTo("설악 야영장");
        assertThat(marker.lat()).isCloseTo(SEORAK.lat(), within(1e-9));
        assertThat(marker.lng()).isCloseTo(SEORAK.lng(), within(1e-9));
        assertThat(marker.parkWarning()).isFalse();
        assertThat(marker.closedNow()).isFalse();
    }

    @Test
    @DisplayName("[F-03] 영역 경계선 위의 장소도 찾고, 경계선 바로 밖의 장소는 뺀다")
    void includesSpotsOnAreaEdgeAndExcludesSpotsJustOutside() {
        // given
        long southEdgeId = insertSpot("CAMPSITE", "남쪽 경계 야영장", new Coordinate(37.5, 128.75));
        long westEdgeId = insertSpot("CAMPSITE", "서쪽 경계 야영장", new Coordinate(37.75, 128.5));
        long northEastCornerId = insertSpot("CAMPSITE", "북동 꼭짓점 야영장", new Coordinate(38.0, 129.0));
        insertSpot("CAMPSITE", "남쪽 경계 밖 야영장", new Coordinate(37.4999, 128.75));
        insertSpot("CAMPSITE", "동쪽 경계 밖 야영장", new Coordinate(37.75, 129.0001));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(result.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(southEdgeId, westEdgeId, northEastCornerId);
    }

    @Test
    @DisplayName("[F-03] 숨김, 검토 대기, 삭제 상태의 장소는 지도에 나오지 않는다")
    void excludesSpotsThatAreNotActive() {
        // given
        long activeId = insertSpot("CAMPSITE", "ACTIVE", false, SEORAK);
        insertSpot("CAMPSITE", "HIDDEN", false, SEORAK);
        insertSpot("BAKJI", "PENDING_REVIEW", false, SEORAK);
        insertSpot("CAMPSITE", "DELETED", false, SEORAK);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(result.markers()).extracting(SpotMarker::spotId).containsExactly(activeId);
    }

    @Test
    @DisplayName("[F-03] 유형을 지정하면 그 유형의 장소만 찾고, 지정하지 않으면 모든 유형을 찾는다")
    void filtersByTypesOnlyWhenTypesAreGiven() {
        // given
        long campsiteId = insertSpot("CAMPSITE", "야영장", SEORAK);
        long forestId = insertSpot("FOREST", "휴양림", SEORAK);
        long bakjiId = insertSpot("BAKJI", "박지", SEORAK);

        // when
        SpotAreaResult filtered = spotMapQueryService.findInArea(
                SEORAK_AREA.query(Set.of(SpotType.FOREST, SpotType.BAKJI), false, false, false));
        SpotAreaResult all = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(filtered.markers()).extracting(SpotMarker::spotId).containsExactly(forestId, bakjiId);
        assertThat(all.markers()).extracting(SpotMarker::spotId).containsExactly(campsiteId, forestId, bakjiId);
    }

    @Test
    @DisplayName("[F-03] 공원 경계 경고가 붙은 장소는 경고 표시와 함께 나오고, 경고 제외를 켜면 빠진다")
    void reportsParkWarningAndExcludesWarnedSpotsOnRequest() {
        // given
        long warnedId = insertSpot("BAKJI", "ACTIVE", true, SEORAK);
        long plainId = insertSpot("BAKJI", "ACTIVE", false, SEORAK);

        // when
        SpotAreaResult withWarned = spotMapQueryService.findInArea(SEORAK_AREA.query());
        SpotAreaResult withoutWarned = spotMapQueryService.findInArea(SEORAK_AREA.query(Set.of(), false, false, true));

        // then
        assertThat(withWarned.markers())
                .extracting(SpotMarker::spotId, SpotMarker::parkWarning)
                .containsExactly(tuple(warnedId, true), tuple(plainId, false));
        assertThat(withoutWarned.markers()).extracting(SpotMarker::spotId).containsExactly(plainId);
    }

    @Test
    @DisplayName("[F-03] 물·화장실 필터는 해당 시설이 있는 박지만 남기고, 야영장과 휴양림은 시설과 상관없이 남긴다")
    void facilityFiltersApplyOnlyToBakji() {
        // given
        long reporterId = insertMember();
        long campsiteId = insertSpot("CAMPSITE", "야영장", SEORAK);
        long forestId = insertSpot("FOREST", "휴양림", SEORAK);
        long waterOnlyId = insertBakji(reporterId, true, false);
        long toiletOnlyId = insertBakji(reporterId, false, true);
        long bothId = insertBakji(reporterId, true, true);
        long noneId = insertBakji(reporterId, false, false);

        // when
        SpotAreaResult water = spotMapQueryService.findInArea(SEORAK_AREA.query(Set.of(), true, false, false));
        SpotAreaResult toilet = spotMapQueryService.findInArea(SEORAK_AREA.query(Set.of(), false, true, false));
        SpotAreaResult both = spotMapQueryService.findInArea(SEORAK_AREA.query(Set.of(), true, true, false));
        SpotAreaResult none = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(water.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(campsiteId, forestId, waterOnlyId, bothId);
        assertThat(toilet.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(campsiteId, forestId, toiletOnlyId, bothId);
        assertThat(both.markers()).extracting(SpotMarker::spotId).containsExactly(campsiteId, forestId, bothId);
        assertThat(none.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(campsiteId, forestId, waterOnlyId, toiletOnlyId, bothId, noneId);
    }

    @Test
    @DisplayName("[F-03] 휴장 여부는 한국 날짜로 계산하고, 휴장 중인 장소도 지도에서 숨기지 않는다")
    void computesClosedNowWithKoreanDateAndKeepsClosedSpots() {
        // given
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        long reporterId = insertMember();
        long closedPeriodId = insertSpot("CAMPSITE", "휴장 기간 중 야영장", SEORAK);
        insertPublicDetail(
                closedPeriodId, "GOCAMPING", "OPERATING", LocalDate.parse("2026-11-01"), LocalDate.parse("2027-03-31"));
        long endedPeriodId = insertSpot("CAMPSITE", "휴장 기간 끝난 야영장", SEORAK);
        insertPublicDetail(
                endedPeriodId,
                "GOCAMPING",
                "TEMPORARILY_CLOSED",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-03-31"));
        long closedStatusId = insertSpot("FOREST", "휴장 상태 휴양림", SEORAK);
        insertPublicDetail(closedStatusId, "FOREST", "TEMPORARILY_CLOSED", null, null);
        long operatingId = insertSpot("CAMPSITE", "운영 중 야영장", SEORAK);
        insertPublicDetail(operatingId, "GOCAMPING", "OPERATING", null, null);
        long bakjiId = insertBakji(reporterId, true, true);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(result.markers())
                .extracting(SpotMarker::spotId, SpotMarker::closedNow)
                .containsExactly(
                        tuple(closedPeriodId, true),
                        tuple(endedPeriodId, false),
                        tuple(closedStatusId, true),
                        tuple(operatingId, false),
                        tuple(bakjiId, false));
    }

    @Test
    @DisplayName("[F-31] 휴장 제외를 켜면 휴장 판정이 참인 장소만 빠지고, 빠진 장소는 휴장 표시가 붙던 장소와 같다")
    void hideClosedExcludesExactlySpotsMarkedClosed() {
        // given
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        long reporterId = insertMember();
        long closedPeriodId = insertSpot("CAMPSITE", "휴장 기간 중 야영장", SEORAK);
        insertPublicDetail(
                closedPeriodId, "GOCAMPING", "OPERATING", LocalDate.parse("2026-11-01"), LocalDate.parse("2027-03-31"));
        long endedPeriodId = insertSpot("CAMPSITE", "휴장 기간 끝난 야영장", SEORAK);
        insertPublicDetail(
                endedPeriodId,
                "GOCAMPING",
                "TEMPORARILY_CLOSED",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-10-31"));
        long openEndedId = insertSpot("CAMPSITE", "종료일 없는 휴장 야영장", SEORAK);
        insertPublicDetail(openEndedId, "GOCAMPING", "OPERATING", LocalDate.parse("2026-10-01"), null);
        long notStartedId = insertSpot("CAMPSITE", "휴장 시작 전 야영장", SEORAK);
        insertPublicDetail(notStartedId, "GOCAMPING", "OPERATING", LocalDate.parse("2026-11-02"), null);
        long untilTodayId = insertSpot("CAMPSITE", "오늘까지 휴장 야영장", SEORAK);
        insertPublicDetail(untilTodayId, "GOCAMPING", "OPERATING", null, LocalDate.parse("2026-11-01"));
        long closedStatusId = insertSpot("FOREST", "휴장 상태 휴양림", SEORAK);
        insertPublicDetail(closedStatusId, "FOREST", "PERMANENTLY_CLOSED", null, null);
        long noStatusId = insertSpot("FOREST", "운영 상태 없는 휴양림", SEORAK);
        insertPublicDetail(noStatusId, "FOREST", null, null, null);
        long noDetailId = insertSpot("CAMPSITE", "상세 없는 야영장", SEORAK);
        long bakjiId = insertBakji(reporterId, false, false);

        // when
        SpotAreaResult all = spotMapQueryService.findInArea(SEORAK_AREA.query());
        SpotAreaResult open = spotMapQueryService.findInArea(SEORAK_AREA.hidingClosed());

        // then
        Set<Long> markedClosed = all.markers().stream()
                .filter(SpotMarker::closedNow)
                .map(SpotMarker::spotId)
                .collect(Collectors.toSet());
        assertThat(markedClosed).containsExactlyInAnyOrder(closedPeriodId, openEndedId, untilTodayId, closedStatusId);
        assertThat(open.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(endedPeriodId, notStartedId, noStatusId, noDetailId, bakjiId)
                .doesNotContainAnyElementsOf(markedClosed);
        assertThat(open.markers()).noneMatch(SpotMarker::closedNow);
    }

    @Test
    @DisplayName("[F-31] 휴장 제외를 켜면 묶음 개수에서도 휴장 장소를 뺀다")
    void hideClosedAlsoAppliesToClusterCounts() {
        // given
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        int openCount = SpotMapQueryService.MAX_ITEMS + 1;
        insertCampsites(spreadInBulkArea(openCount));
        for (int i = 0; i < 3; i++) {
            long closedId = insertSpot("CAMPSITE", "휴장 야영장 " + i, new Coordinate(36.5, 127.5));
            insertPublicDetail(closedId, "GOCAMPING", "TEMPORARILY_CLOSED", null, null);
        }

        // when
        SpotAreaResult all = spotMapQueryService.findInArea(BULK_AREA.query());
        SpotAreaResult open = spotMapQueryService.findInArea(BULK_AREA.hidingClosed());

        // then
        assertThat(all.clusters().stream().mapToLong(SpotCluster::count).sum()).isEqualTo(openCount + 3);
        assertThat(open.markers()).isEmpty();
        assertThat(open.clusters().stream().mapToLong(SpotCluster::count).sum()).isEqualTo(openCount);
    }

    @Test
    @DisplayName("[F-31] 입지를 지정하면 그 입지가 하나라도 맞는 야영장만 찾고, 지정하지 않으면 모두 찾는다")
    void filtersBySettingsOnlyWhenSettingsAreGiven() {
        // given
        long valleyId = insertCampsiteWithSettings("계곡 야영장", SEORAK, "VALLEY");
        long beachId = insertCampsiteWithSettings("해변 야영장", SEORAK, "BEACH");
        long multiId = insertCampsiteWithSettings("산 계곡 야영장", SEORAK, "MOUNTAIN", "VALLEY");
        long noSettingId = insertSpot("CAMPSITE", "입지 없는 야영장", SEORAK);
        long bakjiId = insertSpot("BAKJI", "박지", SEORAK);
        long forestId = insertSpot("FOREST", "휴양림", SEORAK);

        // when
        SpotAreaResult valley =
                spotMapQueryService.findInArea(SEORAK_AREA.queryWithSettings(Set.of(PublicSpotSetting.VALLEY), false));
        SpotAreaResult valleyOrBeach = spotMapQueryService.findInArea(
                SEORAK_AREA.queryWithSettings(Set.of(PublicSpotSetting.VALLEY, PublicSpotSetting.BEACH), false));
        SpotAreaResult all = spotMapQueryService.findInArea(SEORAK_AREA.query());

        // then
        assertThat(valley.markers()).extracting(SpotMarker::spotId).containsExactly(valleyId, multiId);
        assertThat(valleyOrBeach.markers()).extracting(SpotMarker::spotId).containsExactly(valleyId, beachId, multiId);
        assertThat(all.markers())
                .extracting(SpotMarker::spotId)
                .containsExactly(valleyId, beachId, multiId, noSettingId, bakjiId, forestId);
    }

    @Test
    @DisplayName("[F-31] 입지 필터로 500개를 넘으면 입지가 맞는 장소만 직접 세어 묶고, 휴장 제외도 함께 적용한다")
    void settingFilterClustersCountOnlyMatchingSpotsAndApplyHideClosed() {
        // given
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        List<Coordinate> valleyCoordinates = new ArrayList<>(spreadInBulkArea(SpotMapQueryService.MAX_ITEMS + 1));
        valleyCoordinates.add(new Coordinate(BULK_AREA.neLat(), BULK_AREA.neLng()));
        insertBulkCampsitesWithSetting(valleyCoordinates, "VALLEY");
        insertBulkCampsitesWithSetting(spreadInBulkArea(10), "BEACH");
        insertCampsites(spreadInBulkArea(10));
        for (int i = 0; i < 3; i++) {
            long closedId = insertCampsiteWithSettings("휴장 계곡 야영장 " + i, new Coordinate(36.5, 127.5), "VALLEY");
            jdbcTemplate.update(
                    "UPDATE public_spot_detail SET operating_status = 'TEMPORARILY_CLOSED' WHERE spot_id = ?",
                    closedId);
        }
        Set<PublicSpotSetting> valley = Set.of(PublicSpotSetting.VALLEY);

        // when
        SpotAreaResult withClosed = spotMapQueryService.findInArea(BULK_AREA.queryWithSettings(valley, false));
        SpotAreaResult openOnly = spotMapQueryService.findInArea(BULK_AREA.queryWithSettings(valley, true));

        // then
        long matching = valleyCoordinates.size();
        assertThat(withClosed.markers()).isEmpty();
        assertThat(withClosed.clusters().stream().mapToLong(SpotCluster::count).sum())
                .isEqualTo(matching + 3);
        assertThat(openOnly.markers()).isEmpty();
        assertThat(openOnly.clusters().stream().mapToLong(SpotCluster::count).sum())
                .isEqualTo(matching);
    }

    @Test
    @DisplayName("[F-03][F-31] 시설 필터와 휴장 제외를 함께 켜면 마커에는 두 조건을 모두 통과한 장소만 나온다")
    void facilityFilterAndHideClosedTogetherKeepOnlyMarkersPassingBoth() {
        // given
        long reporterId = insertMember();
        long openCampsiteId = insertSpot("CAMPSITE", "운영 중 야영장", SEORAK);
        long closedCampsiteId = insertSpot("CAMPSITE", "휴장 야영장", SEORAK);
        insertPublicDetail(closedCampsiteId, "GOCAMPING", "TEMPORARILY_CLOSED", null, null);
        long waterBakjiId = insertBakji(reporterId, true, false);
        insertBakji(reporterId, false, false);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(BULK_FILTER_QUERY.apply(SEORAK_AREA));

        // then
        assertThat(result.markers()).extracting(SpotMarker::spotId).containsExactly(openCampsiteId, waterBakjiId);
    }

    @Test
    @DisplayName("[F-03][F-31] 묶음 개수에도 시설 필터와 휴장 제외를 각각, 또 함께 적용한다")
    void clusterCountsApplyFacilityFilterAndHideClosedSeparatelyAndTogether() {
        // given
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        int openCount = SpotMapQueryService.MAX_ITEMS + 1;
        insertCampsites(spreadInBulkArea(openCount));
        long reporterId = insertMember();
        Coordinate inBulkArea = new Coordinate(36.5, 127.5);
        for (int i = 0; i < 4; i++) {
            insertBakji(reporterId, true, false, inBulkArea);
        }
        for (int i = 0; i < 3; i++) {
            insertBakji(reporterId, false, false, inBulkArea);
        }
        for (int i = 0; i < 2; i++) {
            long closedId = insertSpot("CAMPSITE", "휴장 야영장 " + i, inBulkArea);
            insertPublicDetail(closedId, "GOCAMPING", "TEMPORARILY_CLOSED", null, null);
        }

        // when
        long all = clusterCount(BULK_AREA.query());
        long water = clusterCount(BULK_AREA.query(Set.of(), true, false, false));
        long open = clusterCount(BULK_AREA.hidingClosed());
        long waterAndOpen = clusterCount(BULK_FILTER_QUERY.apply(BULK_AREA));

        // then
        assertThat(all).isEqualTo(openCount + 4 + 3 + 2);
        assertThat(water).isEqualTo(openCount + 4 + 2);
        assertThat(open).isEqualTo(openCount + 4 + 3);
        assertThat(waterAndOpen).isEqualTo(openCount + 4);
    }

    @Test
    @DisplayName("[F-03] 영역 안 장소가 500개면 모두 마커로 주고 묶음은 비운다")
    void returnsAllMarkersWhenSpotCountIsAtLimit() {
        // given
        insertCampsites(spreadInBulkArea(SpotMapQueryService.MAX_ITEMS));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(BULK_AREA.query());

        // then
        assertThat(result.markers()).hasSize(SpotMapQueryService.MAX_ITEMS);
        assertThat(result.markers()).extracting(SpotMarker::spotId).isSorted();
        assertThat(result.clusters()).isEmpty();
    }

    @Test
    @DisplayName("[F-03] 영역 안 장소가 500개를 넘으면 마커를 비우고 400칸 이하의 묶음으로 모든 장소를 센다")
    void returnsClustersWhenSpotCountExceedsLimit() {
        // given
        int spotCount = SpotMapQueryService.MAX_ITEMS + 1;
        insertCampsites(spreadInBulkArea(spotCount));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(BULK_AREA.query());

        // then
        assertThat(result.markers()).isEmpty();
        assertThat(result.clusters()).isNotEmpty().hasSizeLessThanOrEqualTo(SpotMapQueryService.MAX_CLUSTER_CELLS);
        assertThat(result.clusters().stream().mapToLong(SpotCluster::count).sum())
                .isEqualTo(spotCount);
        assertThat(result.clusters()).allSatisfy(cluster -> {
            assertThat(cluster.lat()).isBetween(BULK_AREA.swLat(), BULK_AREA.neLat());
            assertThat(cluster.lng()).isBetween(BULK_AREA.swLng(), BULK_AREA.neLng());
        });
    }

    @Test
    @DisplayName("[F-03] 묶음은 고정 격자 칸 하나에 들어간 장소의 평균 좌표와 수를 남쪽 행부터, 같은 행에서는 서쪽 칸부터 준다")
    void clustersByFixedGridCellInRowOrderWithAverageOfSpots() {
        // given
        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.addAll(Collections.nCopies(100, new Coordinate(36.101, 127.131)));
        coordinates.addAll(Collections.nCopies(100, new Coordinate(36.141, 127.171)));
        coordinates.addAll(Collections.nCopies(200, new Coordinate(36.095, 127.951)));
        coordinates.addAll(Collections.nCopies(101, new Coordinate(36.911, 127.131)));
        insertCampsites(coordinates);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(BULK_AREA.query());

        // then
        assertThat(result.markers()).isEmpty();
        assertThat(result.clusters()).hasSize(3);
        assertCluster(result.clusters().get(0), new Coordinate(36.121, 127.151), 200);
        assertCluster(result.clusters().get(1), new Coordinate(36.095, 127.951), 200);
        assertCluster(result.clusters().get(2), new Coordinate(36.911, 127.131), 101);
    }

    @Test
    @DisplayName("[F-03] 지도를 조금 옮겨도 두 영역에 모두 중심이 든 격자 칸의 묶음은 위치와 개수가 그대로다")
    void clusterOfCellInsideBothAreasStaysSameWhenAreaIsPanned() {
        // given
        Area panned = new Area(36.01, 127.01, 37.01, 128.01);
        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.addAll(Collections.nCopies(300, new Coordinate(36.5, 127.5)));
        coordinates.addAll(Collections.nCopies(201, new Coordinate(36.7, 127.7)));
        insertCampsites(coordinates);

        // when
        SpotAreaResult original = spotMapQueryService.findInArea(BULK_AREA.query());
        SpotAreaResult moved = spotMapQueryService.findInArea(panned.query());

        // then
        assertThat(original.clusters()).hasSize(2);
        assertThat(moved.clusters()).isEqualTo(original.clusters());
    }

    @Test
    @DisplayName("[F-03] 격자 칸이 가장 큰 격자로도 400개를 넘는 아주 넓은 영역은 화면을 20칸씩 나눠 묶는다")
    void wideAreaFallsBackToTwentyByTwentyViewCells() {
        // given
        Area wide = new Area(30.0, 120.0, 40.0, 130.0);
        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.addAll(Collections.nCopies(300, new Coordinate(36.5, 127.5)));
        coordinates.addAll(Collections.nCopies(201, new Coordinate(36.52, 127.52)));
        insertCampsites(coordinates);

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(wide.query());

        // then
        assertThat(result.markers()).isEmpty();
        assertThat(result.clusters())
                .hasSizeLessThanOrEqualTo(SpotMapQueryService.GRID_SIZE * SpotMapQueryService.GRID_SIZE);
        assertThat(result.clusters()).hasSize(1);
        assertCluster(
                result.clusters().getFirst(),
                new Coordinate((300 * 36.5 + 201 * 36.52) / 501, (300 * 127.5 + 201 * 127.52) / 501),
                501);
    }

    @Test
    @DisplayName("[F-31] 고정 격자 묶음에서 휴장 제외를 켜면 그 칸의 묶음 개수와 평균 좌표에서 휴장 장소가 빠진다")
    void hideClosedSubtractsClosedSpotsFromFixedGridCluster() {
        // given
        clock.setInstant(Instant.parse("2026-10-31T15:30:00Z"));
        insertCampsites(Collections.nCopies(SpotMapQueryService.MAX_ITEMS + 1, new Coordinate(36.2, 127.2)));
        insertCampsites(Collections.nCopies(2, new Coordinate(36.90, 127.90)));
        for (int i = 0; i < 3; i++) {
            long closedId = insertSpot("CAMPSITE", "휴장 야영장 " + i, new Coordinate(36.94, 127.86));
            insertPublicDetail(closedId, "GOCAMPING", "TEMPORARILY_CLOSED", null, null);
        }

        // when
        SpotAreaResult all = spotMapQueryService.findInArea(BULK_AREA.query());
        SpotAreaResult open = spotMapQueryService.findInArea(BULK_AREA.hidingClosed());

        // then
        assertThat(all.clusters()).hasSize(2);
        assertCluster(
                all.clusters().get(1), new Coordinate((2 * 36.90 + 3 * 36.94) / 5, (2 * 127.90 + 3 * 127.86) / 5), 5);
        assertThat(open.clusters()).hasSize(2);
        assertCluster(open.clusters().get(0), new Coordinate(36.2, 127.2), SpotMapQueryService.MAX_ITEMS + 1);
        assertCluster(open.clusters().get(1), new Coordinate(36.90, 127.90), 2);
    }

    @Test
    @DisplayName("[F-03] 고정 격자 묶음에 유형 필터를 적용하면 조합별 표에서 그 유형의 장소만 센다")
    void typeFilterAppliesToFixedGridClusters() {
        // given
        insertCampsites(Collections.nCopies(SpotMapQueryService.MAX_ITEMS + 1, new Coordinate(36.2, 127.2)));
        for (int i = 0; i < 3; i++) {
            insertSpot("FOREST", "휴양림 " + i, new Coordinate(36.9, 127.9));
        }

        // when
        SpotAreaResult campsites =
                spotMapQueryService.findInArea(BULK_AREA.query(Set.of(SpotType.CAMPSITE), false, false, false));
        SpotAreaResult all = spotMapQueryService.findInArea(BULK_AREA.query());

        // then
        assertThat(campsites.markers()).isEmpty();
        assertThat(campsites.clusters()).hasSize(1);
        assertCluster(campsites.clusters().getFirst(), new Coordinate(36.2, 127.2), SpotMapQueryService.MAX_ITEMS + 1);
        assertThat(all.clusters()).hasSize(2);
        assertCluster(all.clusters().get(1), new Coordinate(36.9, 127.9), 3);
    }

    @Test
    @DisplayName("[F-03] 묶음은 격자 칸 중심으로 세서, 가장자리 격자 칸의 장소는 영역 밖이어도 들어오고 영역 안이어도 빠질 수 있다")
    void clustersCountGridCellsByCenterAtAreaEdges() {
        // given
        Area shifted = new Area(36.10, 127.02, 37.07, 128.02);
        int insideCount = SpotMapQueryService.MAX_ITEMS + 1;
        insertCampsites(Collections.nCopies(insideCount, new Coordinate(36.5, 127.5)));
        insertSpot("CAMPSITE", "남쪽 바깥 야영장", new Coordinate(36.085, 127.905));
        insertSpot("CAMPSITE", "북쪽 안쪽 야영장", new Coordinate(37.06, 127.905));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(shifted.query());

        // then
        assertThat(result.markers()).isEmpty();
        assertThat(result.clusters().stream().mapToLong(SpotCluster::count).sum())
                .isEqualTo(insideCount + 1);
        assertThat(result.clusters())
                .anySatisfy(cluster -> assertThat(cluster.lat()).isLessThan(shifted.swLat()));
        assertThat(result.clusters())
                .allSatisfy(cluster -> assertThat(cluster.lat()).isLessThan(37.04));
    }

    @Test
    @DisplayName("[F-03] 영역 안 장소가 500개를 넘는지는 격자와 상관없이 정확히 판단한다")
    void decidesMarkersOrClustersByExactCountInArea() {
        // given
        Area shifted = new Area(36.005, 127.005, 37.005, 128.005);
        insertCampsites(spreadInBulkArea(SpotMapQueryService.MAX_ITEMS));
        insertSpot("CAMPSITE", "남쪽 바깥 야영장", new Coordinate(36.001, 127.905));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(shifted.query());

        // then
        assertThat(result.clusters()).isEmpty();
        assertThat(result.markers()).hasSize(SpotMapQueryService.MAX_ITEMS);
    }

    @Test
    @DisplayName("[F-03] 거친 격자로 세어 상한을 못 넘는다고 나와도, 실제로 500개를 넘으면 마커를 읽어 판단해 묶음을 준다")
    void returnsClustersByMarkerFallbackWhenCoarseCountUnderCounts() {
        // given
        Area shifted = new Area(36.0125, 127.0, 37.0125, 128.0);
        int spotCount = SpotMapQueryService.MAX_ITEMS + 1;
        insertCampsites(Collections.nCopies(spotCount, new Coordinate(36.03, 127.5)));

        // when
        SpotAreaResult result = spotMapQueryService.findInArea(shifted.query());

        // then
        assertThat(result.markers()).isEmpty();
        assertThat(result.clusters().stream().mapToLong(SpotCluster::count).sum())
                .isEqualTo(spotCount);
    }

    private static void assertCluster(SpotCluster cluster, Coordinate expectedCenter, long expectedCount) {
        assertThat(cluster.lat()).isCloseTo(expectedCenter.lat(), within(1e-9));
        assertThat(cluster.lng()).isCloseTo(expectedCenter.lng(), within(1e-9));
        assertThat(cluster.count()).isEqualTo(expectedCount);
    }

    private static List<Coordinate> spreadInBulkArea(int count) {
        List<Coordinate> coordinates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            coordinates.add(new Coordinate(
                    BULK_AREA.swLat() + (i % 25) * 0.04 + 0.01, BULK_AREA.swLng() + (i / 25) * 0.04 + 0.01));
        }
        return coordinates;
    }

    private long insertSpot(String type, String name, Coordinate at) {
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                type,
                name,
                at.wkt());
        return findSpotId(name);
    }

    private long insertSpot(String type, String status, boolean parkWarning, Coordinate at) {
        String name = TestSequence.unique(status);
        jdbcTemplate.update(
                "INSERT INTO spot"
                        + " (type, name, location, weather_nx, weather_ny, park_warning, status, created_at, updated_at)"
                        + " VALUES (?, ?, ST_GeomFromText(?, 4326), 60, 127, ?, ?, NOW(6), NOW(6))",
                type,
                name,
                at.wkt(),
                parkWarning,
                status);
        return findSpotId(name);
    }

    private long clusterCount(SpotAreaQuery query) {
        SpotAreaResult result = spotMapQueryService.findInArea(query);
        assertThat(result.markers()).isEmpty();
        return result.clusters().stream().mapToLong(SpotCluster::count).sum();
    }

    private long insertBakji(long reporterId, boolean hasWater, boolean hasToilet) {
        return insertBakji(reporterId, hasWater, hasToilet, SEORAK);
    }

    private long insertBakji(long reporterId, boolean hasWater, boolean hasToilet, Coordinate at) {
        long spotId = insertSpot("BAKJI", TestSequence.unique("박지"), at);
        jdbcTemplate.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
                spotId,
                reporterId,
                hasWater,
                hasToilet);
        return spotId;
    }

    private void insertPublicDetail(
            long spotId, String source, String operatingStatus, LocalDate closedFrom, LocalDate closedUntil) {
        jdbcTemplate.update(
                "INSERT INTO public_spot_detail"
                        + " (spot_id, source, external_id, operating_status, closed_from, closed_until,"
                        + " synced_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))",
                spotId,
                source,
                String.valueOf(spotId),
                operatingStatus,
                closedFrom,
                closedUntil);
    }

    private void insertCampsites(List<Coordinate> coordinates) {
        String values = coordinates.stream()
                .map(at -> String.format(
                        Locale.ROOT,
                        "('CAMPSITE', '%s', ST_GeomFromText('%s', 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                        TestSequence.unique("bulk"),
                        at.wkt()))
                .collect(Collectors.joining(", "));
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at) VALUES "
                        + values);
    }

    private long insertCampsiteWithSettings(String name, Coordinate at, String... settings) {
        long spotId = insertSpot("CAMPSITE", name, at);
        insertPublicDetail(spotId, "GOCAMPING", null, null, null);
        insertSettings(spotId, settings);
        return spotId;
    }

    private void insertSettings(long spotId, String... settings) {
        for (String setting : settings) {
            jdbcTemplate.update("INSERT INTO public_spot_setting (spot_id, setting) VALUES (?, ?)", spotId, setting);
        }
    }

    private void insertBulkCampsitesWithSetting(List<Coordinate> coordinates, String setting) {
        insertCampsites(coordinates);
        List<Long> spotIds = jdbcTemplate.queryForList(
                "SELECT s.id FROM spot s LEFT JOIN public_spot_detail d ON d.spot_id = s.id"
                        + " WHERE s.type = 'CAMPSITE' AND d.spot_id IS NULL AND s.name LIKE 'bulk%'",
                Long.class);
        spotIds.forEach(spotId -> insertPublicDetail(spotId, "GOCAMPING", null, null, null));
        spotIds.forEach(spotId -> insertSettings(spotId, setting));
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

    private long findSpotId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private record Coordinate(double lat, double lng) {

        // SRID 4326 좌표의 WKT는 위도, 경도 순서다.
        String wkt() {
            return String.format(Locale.ROOT, "POINT(%s %s)", lat, lng);
        }
    }

    private record Area(double swLat, double swLng, double neLat, double neLng) {

        SpotAreaQuery query() {
            return query(Set.of(), false, false, false);
        }

        SpotAreaQuery query(Set<SpotType> types, boolean hasWater, boolean hasToilet, boolean excludeWarning) {
            return new SpotAreaQuery(
                    swLat, swLng, neLat, neLng, types, Set.of(), hasWater, hasToilet, excludeWarning, false);
        }

        SpotAreaQuery queryWithSettings(Set<PublicSpotSetting> settings, boolean hideClosed) {
            return new SpotAreaQuery(swLat, swLng, neLat, neLng, Set.of(), settings, false, false, false, hideClosed);
        }

        SpotAreaQuery hidingClosed() {
            return new SpotAreaQuery(swLat, swLng, neLat, neLng, Set.of(), Set.of(), false, false, false, true);
        }
    }
}
