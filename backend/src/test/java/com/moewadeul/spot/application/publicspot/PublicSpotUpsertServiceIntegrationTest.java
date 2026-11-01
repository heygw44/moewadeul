package com.moewadeul.spot.application.publicspot;

import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.CHUNCHEON_LATITUDE;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.CHUNCHEON_LONGITUDE;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.NEXT_SYNC;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.SEOUL_LATITUDE;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.SEOUL_LONGITUDE;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.campsite;
import static com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.forest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncFixture.StoredSpot;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.SpotRepository;
import com.moewadeul.spot.domain.WeatherGrid;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class PublicSpotUpsertServiceIntegrationTest {

    private static final TypeReference<Map<String, String>> FACILITIES_TYPE = new TypeReference<>() {};

    @Autowired
    private PublicSpotSyncService publicSpotSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private MutableClock clock;

    private PublicSpotSyncFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new PublicSpotSyncFixture(jdbcTemplate);
    }

    @Test
    @DisplayName("[F-06] 처음 적재하면 고캠핑 야영장을 ACTIVE 상태의 장소와 공공데이터 상세로 저장한다")
    void firstUpsertInsertsSpotAndDetail() {
        // given
        Map<String, String> facilities = Map.of("sbrsCl", "전기,무선인터넷", "toiletCo", "4", "animalCmgCl", "불가능");
        PublicSpotCommand command = new PublicSpotCommand(
                "100",
                "솔숲 야영장",
                CHUNCHEON_LATITUDE,
                CHUNCHEON_LONGITUDE,
                "강원 춘천시 남산면 1",
                "일반야영장",
                facilities,
                "033-000-0000",
                "https://example.com",
                null,
                null,
                null,
                null,
                Set.of());

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 0));
        WeatherGrid expectedGrid = WeatherGrid.from(new GeoPoint(CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE));
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.type()).isEqualTo("CAMPSITE");
        assertThat(spot.status()).isEqualTo("ACTIVE");
        assertThat(spot.name()).isEqualTo("솔숲 야영장");
        assertThat(spot.address()).isEqualTo("강원 춘천시 남산면 1");
        assertThat(new WeatherGrid(spot.weatherNx(), spot.weatherNy())).isEqualTo(expectedGrid);
        assertThat(spot.parkWarning()).isFalse();
        assertThat(spot.createdAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.category()).isEqualTo("일반야영장");
        assertThat(jsonMapper.readValue(spot.facilities(), FACILITIES_TYPE)).isEqualTo(facilities);
        assertThat(spot.phone()).isEqualTo("033-000-0000");
        assertThat(spot.homepage()).isEqualTo("https://example.com");
        assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 적재한 좌표를 MySQL과 JPA가 모두 같은 위도·경도로 읽는다")
    void upsertedCoordinateIsReadBackWithSameLatitudeAndLongitude() {
        // given
        PublicSpotCommand command = campsite("100", "서울시청 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE);

        // when
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(command));

        // then
        long spotId = fixture.findSpotId(PublicSpotSource.GOCAMPING, "100");
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT ST_Latitude(location) AS latitude, ST_Longitude(location) AS longitude,"
                        + " ST_SRID(location) AS srid, ST_AsText(location) AS wkt FROM spot WHERE id = ?",
                spotId);
        assertThat((Double) stored.get("latitude")).isCloseTo(SEOUL_LATITUDE, within(1e-9));
        assertThat((Double) stored.get("longitude")).isCloseTo(SEOUL_LONGITUDE, within(1e-9));
        assertThat(((Number) stored.get("srid")).intValue()).isEqualTo(4326);
        // SRID 4326 좌표의 WKT는 위도, 경도 순서다.
        assertThat(stored.get("wkt")).isEqualTo("POINT(37.5665 126.978)");
        assertThat(spotRepository.findById(spotId).orElseThrow().getLocation())
                .isEqualTo(new GeoPoint(SEOUL_LATITUDE, SEOUL_LONGITUDE));
    }

    @Test
    @DisplayName("[F-06] 같은 데이터를 두 번 적재하면 행이 늘지 않고 수정 시각과 동기화 시각도 바뀌지 않는다")
    void upsertingSameBatchTwiceLeavesRowsUntouched() {
        // given: MySQL은 JSON 키 순서와 공백을 바꿔 저장하므로, 문자열로 비교하면 바뀐 것으로 잘못 본다.
        List<PublicSpotCommand> batch = List.of(
                campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE),
                campsite("101", "갈대 야영장", CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE));
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 2, 0));
        assertThat(fixture.count("spot")).isEqualTo(2);
        assertThat(fixture.count("public_spot_detail")).isEqualTo(2);
        for (String externalId : List.of("100", "101")) {
            StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, externalId);
            assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
            assertThat(spot.detailUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
            assertThat(spot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        }
    }

    @Test
    @DisplayName("[F-06] 좌표가 바뀐 장소만 갱신하고 기상청 격자를 다시 계산한다")
    void updatesOnlyChangedSpotAndRecomputesWeatherGrid() {
        // given
        PublicSpotCommand unchanged = campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE);
        PublicSpotCommand moving = campsite("101", "갈대 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE);
        publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(unchanged, moving));
        clock.advance(NEXT_SYNC);
        PublicSpotCommand moved = campsite("101", "갈대 야영장", CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE);

        // when
        PublicSpotUpsertResult result =
                publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(unchanged, moved));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 1, 0));
        Instant syncTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot movedSpot = fixture.findSpot(PublicSpotSource.GOCAMPING, "101");
        assertThat(movedSpot.latitude()).isCloseTo(CHUNCHEON_LATITUDE, within(1e-9));
        assertThat(movedSpot.longitude()).isCloseTo(CHUNCHEON_LONGITUDE, within(1e-9));
        assertThat(new WeatherGrid(movedSpot.weatherNx(), movedSpot.weatherNy()))
                .isEqualTo(WeatherGrid.from(new GeoPoint(CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE)));
        assertThat(movedSpot.createdAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(movedSpot.updatedAt()).isEqualTo(syncTime);
        assertThat(movedSpot.syncedAt()).isEqualTo(syncTime);
        StoredSpot untouchedSpot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(untouchedSpot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(untouchedSpot.syncedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 시설 정보만 바뀌면 상세와 동기화 시각만 갱신하고 장소의 수정 시각은 그대로 둔다")
    void detailOnlyChangeUpdatesDetailButNotSpot() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE)));
        clock.advance(NEXT_SYNC);
        Map<String, String> newFacilities = Map.of("sbrsCl", "전기,온수", "toiletCo", "6");
        PublicSpotCommand changed = new PublicSpotCommand(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                "서울 중구 세종대로 110",
                "일반야영장",
                newFacilities,
                "02-000-0000",
                "https://example.com",
                null,
                null,
                null,
                null,
                Set.of());

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of(changed));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        Instant syncTime = MutableClock.DEFAULT_INSTANT.plus(NEXT_SYNC);
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(jsonMapper.readValue(spot.facilities(), FACILITIES_TYPE)).isEqualTo(newFacilities);
        assertThat(spot.syncedAt()).isEqualTo(syncTime);
        assertThat(spot.detailUpdatedAt()).isEqualTo(syncTime);
        assertThat(spot.updatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-06] 관리자가 숨긴 장소는 동기화로 이름이 바뀌어도 숨김 상태와 공원 경고를 유지한다")
    void updateKeepsHiddenStatusAndParkWarning() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE)));
        long spotId = fixture.findSpotId(PublicSpotSource.GOCAMPING, "100");
        jdbcTemplate.update(
                "UPDATE spot SET status = 'HIDDEN', park_warning = TRUE, area_checked_at = ? WHERE id = ?",
                Timestamp.from(MutableClock.DEFAULT_INSTANT),
                spotId);
        clock.advance(NEXT_SYNC);

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsite("100", "솔숲 야영장(새 이름)", SEOUL_LATITUDE, SEOUL_LONGITUDE)));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 1, 0, 0));
        StoredSpot spot = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        assertThat(spot.name()).isEqualTo("솔숲 야영장(새 이름)");
        assertThat(spot.status()).isEqualTo("HIDDEN");
        assertThat(spot.parkWarning()).isTrue();
        Integer areaChecked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spot WHERE id = ? AND area_checked_at IS NOT NULL", Integer.class, spotId);
        assertThat(areaChecked).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06] 기상청 격자 밖 좌표의 장소는 건너뛰고 나머지 장소는 저장한다")
    void skipsSpotOutsideWeatherGridAndStoresOthers() {
        // given
        List<PublicSpotCommand> batch =
                List.of(campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE), campsite("101", "적도 야영장", 0, 0));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 1));
        assertThat(fixture.count("spot")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT external_id FROM public_spot_detail", String.class))
                .containsExactly("100");
    }

    @Test
    @DisplayName("[F-06] 한 묶음에 같은 외부 ID가 두 번 있으면 마지막 값 하나만 저장한다")
    void keepsLastCommandWhenExternalIdRepeatsInBatch() {
        // given
        List<PublicSpotCommand> batch = List.of(
                campsite("100", "옛 이름 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE),
                campsite("100", "새 이름 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, batch);

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 1));
        assertThat(fixture.count("spot")).isEqualTo(1);
        assertThat(fixture.findSpot(PublicSpotSource.GOCAMPING, "100").name()).isEqualTo("새 이름 야영장");
    }

    @Test
    @DisplayName("[F-06] 자연휴양림의 외부 ID가 고캠핑 야영장과 같아도 별도 장소로 저장한다")
    void sameExternalIdFromDifferentSourceCreatesSeparateSpot() {
        // given
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING, List.of(campsite("100", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE)));

        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(
                PublicSpotSource.FOREST, List.of(campsite("100", "솔숲 휴양림", CHUNCHEON_LATITUDE, CHUNCHEON_LONGITUDE)));

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(1, 0, 0, 0));
        assertThat(fixture.count("spot")).isEqualTo(2);
        StoredSpot campsite = fixture.findSpot(PublicSpotSource.GOCAMPING, "100");
        StoredSpot forest = fixture.findSpot(PublicSpotSource.FOREST, "100");
        assertThat(campsite.type()).isEqualTo("CAMPSITE");
        assertThat(campsite.name()).isEqualTo("솔숲 야영장");
        assertThat(forest.type()).isEqualTo("FOREST");
        assertThat(forest.name()).isEqualTo("솔숲 휴양림");
    }

    @Test
    @DisplayName("[F-06] 빈 묶음을 적재하면 아무것도 저장하지 않고 모든 건수가 0이다")
    void emptyBatchStoresNothing() {
        // when
        PublicSpotUpsertResult result = publicSpotSyncService.upsert(PublicSpotSource.GOCAMPING, List.of());

        // then
        assertThat(result).isEqualTo(new PublicSpotUpsertResult(0, 0, 0, 0));
        assertThat(fixture.count("spot")).isZero();
    }
}
